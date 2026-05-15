#include <jni.h>

#include <Eigen/Dense>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
#include <map>
#include <nlohmann/json.hpp>
#include <string>
#include <vector>

using json = nlohmann::json;

namespace {

constexpr const char* kSolverName = "native_ceres_metric_pose_optimizer";
constexpr const char* kBackendName = "eigen_extrinsic_metric_pose_optimizer";

struct Intrinsics {
    double fx = 1.0;
    double fy = 1.0;
};

struct Pose {
    std::string frame_id;
    Eigen::Matrix3d rotation = Eigen::Matrix3d::Identity();
    Eigen::Vector3d translation_mm = Eigen::Vector3d::Zero();
};

struct Observation {
    std::string frame_id;
    double x = 0.0;
    double y = 0.0;
};

struct Track {
    std::string track_id;
    std::vector<Observation> observations;
};

struct MarkerCornerObservation {
    std::string frame_id;
    std::string marker_id;
    int corner_index = 0;
    double x = 0.0;
    double y = 0.0;
    Eigen::Vector3d world_xyz_mm = Eigen::Vector3d::Zero();
};

struct OptimizedPoint {
    std::string point_id;
    std::string track_id;
    Eigen::Vector3d xyz_mm = Eigen::Vector3d::Zero();
    double max_residual_px = 0.0;
    double mean_residual_px = 0.0;
    int residual_count = 0;
    bool accepted = false;
    std::string rejection_reason;
};

Eigen::Matrix3d skew(const Eigen::Vector3d& value)
{
    Eigen::Matrix3d matrix;
    matrix <<
        0.0, -value.z(), value.y(),
        value.z(), 0.0, -value.x(),
        -value.y(), value.x(), 0.0;
    return matrix;
}

Eigen::Matrix3d exp_so3(const Eigen::Vector3d& omega)
{
    const double theta = omega.norm();
    const Eigen::Matrix3d omega_hat = skew(omega);
    if (theta < 1e-12) {
        return Eigen::Matrix3d::Identity() + omega_hat;
    }
    const double sin_theta = std::sin(theta);
    const double cos_theta = std::cos(theta);
    return Eigen::Matrix3d::Identity()
        + (sin_theta / theta) * omega_hat
        + ((1.0 - cos_theta) / (theta * theta)) * (omega_hat * omega_hat);
}

Eigen::Matrix3d orthonormalized(const Eigen::Matrix3d& rotation)
{
    Eigen::JacobiSVD<Eigen::Matrix3d> svd(rotation, Eigen::ComputeFullU | Eigen::ComputeFullV);
    Eigen::Matrix3d corrected = svd.matrixU() * svd.matrixV().transpose();
    if (corrected.determinant() < 0.0) {
        Eigen::Matrix3d u = svd.matrixU();
        u.col(2) *= -1.0;
        corrected = u * svd.matrixV().transpose();
    }
    return corrected;
}

Eigen::Matrix3d matrix_from_json(const json& value)
{
    Eigen::Matrix3d matrix = Eigen::Matrix3d::Identity();
    if (!value.is_array() || value.size() != 9) {
        return matrix;
    }
    for (int row = 0; row < 3; ++row) {
        for (int col = 0; col < 3; ++col) {
            matrix(row, col) = value.at(static_cast<size_t>(row * 3 + col)).get<double>();
        }
    }
    return matrix;
}

Eigen::Vector3d vector3_from_json(const json& value)
{
    if (!value.is_array() || value.size() != 3) {
        return Eigen::Vector3d::Zero();
    }
    return Eigen::Vector3d(
        value.at(0).get<double>(),
        value.at(1).get<double>(),
        value.at(2).get<double>()
    );
}

std::map<std::string, Intrinsics> parse_intrinsics(const json& request)
{
    std::map<std::string, Intrinsics> output;
    const auto& by_frame = request.value("fixed_intrinsics_by_frame", json::object());
    if (!by_frame.is_object()) {
        return output;
    }
    for (auto it = by_frame.begin(); it != by_frame.end(); ++it) {
        const auto& value = it.value();
        Intrinsics intrinsics;
        intrinsics.fx = value.value("fx", 1.0);
        intrinsics.fy = value.value("fy", 1.0);
        output[it.key()] = intrinsics;
    }
    return output;
}

std::map<std::string, Pose> parse_poses(const json& request)
{
    std::map<std::string, Pose> output;
    const auto& poses = request.value("candidate_camera_poses", json::array());
    if (!poses.is_array()) {
        return output;
    }
    for (const auto& value : poses) {
        const std::string frame_id = value.value("frame_id", "");
        if (frame_id.empty()) {
            continue;
        }
        Pose pose;
        pose.frame_id = frame_id;
        pose.rotation = matrix_from_json(value.value("rotation_row_major", json::array()));
        if (value.contains("translation_mm")) {
            pose.translation_mm = vector3_from_json(value.at("translation_mm"));
        } else {
            // Current Kotlin candidates still expose graph translations. They
            // are not accepted as metric by Kotlin gates, but using them here
            // lets the native backend produce deterministic diagnostics.
            pose.translation_mm = vector3_from_json(value.value("translation_graph_units", json::array()));
        }
        output[frame_id] = pose;
    }
    return output;
}

std::vector<Track> parse_tracks(const json& request)
{
    std::vector<Track> output;
    const auto& tracks = request.value("feature_track_observations", json::array());
    if (!tracks.is_array()) {
        return output;
    }
    for (const auto& track_json : tracks) {
        Track track;
        track.track_id = track_json.value("track_id", "");
        const auto& observations = track_json.value("normalized_observations", json::array());
        if (track.track_id.empty() || !observations.is_array()) {
            continue;
        }
        for (const auto& observation_json : observations) {
            Observation observation;
            observation.frame_id = observation_json.value("frame_id", "");
            observation.x = observation_json.value("x_normalized_camera", 0.0);
            observation.y = observation_json.value("y_normalized_camera", 0.0);
            if (!observation.frame_id.empty()) {
                track.observations.push_back(observation);
            }
        }
        if (track.observations.size() >= 2) {
            output.push_back(track);
        }
    }
    return output;
}

std::vector<MarkerCornerObservation> parse_marker_corner_observations(const json& request)
{
    std::vector<MarkerCornerObservation> output;
    const auto& observations = request.value("marker_corner_observations", json::array());
    if (!observations.is_array()) {
        return output;
    }
    for (const auto& observation_json : observations) {
        MarkerCornerObservation observation;
        observation.frame_id = observation_json.value("frame_id", "");
        observation.marker_id = observation_json.value("marker_id", "");
        observation.corner_index = observation_json.value("corner_index", 0);
        observation.x = observation_json.value("observed_x_normalized_camera", 0.0);
        observation.y = observation_json.value("observed_y_normalized_camera", 0.0);
        observation.world_xyz_mm = vector3_from_json(observation_json.value("world_xyz_mm", json::array()));
        if (
            !observation.frame_id.empty() &&
            !observation.marker_id.empty() &&
            observation.world_xyz_mm.allFinite() &&
            std::isfinite(observation.x) &&
            std::isfinite(observation.y)
        ) {
            output.push_back(observation);
        }
    }
    return output;
}

bool nullable_double(const json& value, const char* key, double* output)
{
    if (!value.is_object() || !value.contains(key) || value.at(key).is_null()) {
        return false;
    }
    if (!value.at(key).is_number()) {
        return false;
    }
    *output = value.at(key).get<double>();
    return std::isfinite(*output);
}

double huber_weight(double residual_norm_px, double delta_px)
{
    if (!std::isfinite(residual_norm_px) || residual_norm_px <= 0.0) {
        return 1.0;
    }
    if (delta_px <= 0.0 || residual_norm_px <= delta_px) {
        return 1.0;
    }
    return delta_px / residual_norm_px;
}

bool triangulate_track(
    const Track& track,
    const std::map<std::string, Pose>& poses,
    Eigen::Vector3d* point_mm)
{
    Eigen::Matrix3d normal = Eigen::Matrix3d::Zero();
    Eigen::Vector3d rhs = Eigen::Vector3d::Zero();
    int usable = 0;
    for (const auto& observation : track.observations) {
        const auto pose_it = poses.find(observation.frame_id);
        if (pose_it == poses.end()) {
            continue;
        }
        const Pose& pose = pose_it->second;
        Eigen::Vector3d camera_ray(observation.x, observation.y, 1.0);
        if (camera_ray.norm() <= std::numeric_limits<double>::epsilon()) {
            continue;
        }
        camera_ray.normalize();
        const Eigen::Vector3d world_ray = pose.rotation.transpose() * camera_ray;
        const Eigen::Matrix3d projector = Eigen::Matrix3d::Identity() - world_ray * world_ray.transpose();
        normal += projector;
        rhs += projector * pose.translation_mm;
        usable += 1;
    }
    if (usable < 2 || std::abs(normal.determinant()) < 1e-9) {
        return false;
    }
    *point_mm = normal.ldlt().solve(rhs);
    return point_mm->allFinite();
}

double reprojection_residual_px(
    const Eigen::Vector3d& point_mm,
    const Observation& observation,
    const Pose& pose,
    const Intrinsics& intrinsics)
{
    const Eigen::Vector3d camera_point = pose.rotation * (point_mm - pose.translation_mm);
    if (std::abs(camera_point.z()) < 1e-9) {
        return std::numeric_limits<double>::infinity();
    }
    const double x = camera_point.x() / camera_point.z();
    const double y = camera_point.y() / camera_point.z();
    const double dx = (x - observation.x) * intrinsics.fx;
    const double dy = (y - observation.y) * intrinsics.fy;
    return std::sqrt(dx * dx + dy * dy);
}

bool projection_residual_and_jacobians(
    const Eigen::Vector3d& point_mm,
    const Observation& observation,
    const Pose& pose,
    const Intrinsics& intrinsics,
    Eigen::Vector2d* residual_px,
    Eigen::Matrix<double, 2, 3>* point_jacobian,
    Eigen::Matrix<double, 2, 3>* translation_jacobian,
    Eigen::Matrix<double, 2, 3>* rotation_jacobian = nullptr)
{
    const Eigen::Vector3d camera_point = pose.rotation * (point_mm - pose.translation_mm);
    const double z = camera_point.z();
    if (std::abs(z) < 1e-9) {
        return false;
    }
    const double x = camera_point.x();
    const double y = camera_point.y();
    const double predicted_x = x / z;
    const double predicted_y = y / z;
    (*residual_px)(0) = (predicted_x - observation.x) * intrinsics.fx;
    (*residual_px)(1) = (predicted_y - observation.y) * intrinsics.fy;

    Eigen::Matrix<double, 2, 3> projection;
    projection <<
        intrinsics.fx / z, 0.0, -intrinsics.fx * x / (z * z),
        0.0, intrinsics.fy / z, -intrinsics.fy * y / (z * z);
    if (point_jacobian != nullptr) {
        *point_jacobian = projection * pose.rotation;
    }
    if (translation_jacobian != nullptr) {
        *translation_jacobian = projection * (-pose.rotation);
    }
    if (rotation_jacobian != nullptr) {
        // Left-multiplied SO(3) update: R_new = exp(delta) * R.
        // d(exp(delta) * c) / d(delta) at zero is -skew(c).
        *rotation_jacobian = projection * (-skew(camera_point));
    }
    return true;
}

bool refine_point_gauss_newton(
    const Track& track,
    const std::map<std::string, Pose>& poses,
    const std::map<std::string, Intrinsics>& intrinsics,
    int max_iterations,
    double robust_delta_px,
    Eigen::Vector3d* point_mm)
{
    bool improved = false;
    const int iterations = std::max(1, std::min(max_iterations, 12));
    for (int iteration = 0; iteration < iterations; ++iteration) {
        Eigen::Matrix3d normal = Eigen::Matrix3d::Zero();
        Eigen::Vector3d rhs = Eigen::Vector3d::Zero();
        int residual_count = 0;
        for (const auto& observation : track.observations) {
            const auto pose_it = poses.find(observation.frame_id);
            const auto intrinsics_it = intrinsics.find(observation.frame_id);
            if (pose_it == poses.end() || intrinsics_it == intrinsics.end()) {
                continue;
            }
            Eigen::Vector2d residual;
            Eigen::Matrix<double, 2, 3> jacobian;
            if (!projection_residual_and_jacobians(
                    *point_mm,
                    observation,
                    pose_it->second,
                    intrinsics_it->second,
                    &residual,
                    &jacobian,
                    nullptr)) {
                continue;
            }
            const double weight = huber_weight(residual.norm(), robust_delta_px);
            normal += weight * jacobian.transpose() * jacobian;
            rhs += weight * jacobian.transpose() * residual;
            residual_count += 1;
        }
        if (residual_count < 2 || std::abs(normal.determinant()) < 1e-12) {
            return improved;
        }
        const Eigen::Vector3d delta = -normal.ldlt().solve(rhs);
        if (!delta.allFinite()) {
            return improved;
        }
        *point_mm += delta;
        improved = true;
        if (delta.norm() < 1e-5) {
            return improved;
        }
    }
    return improved;
}

void measure_point_residuals(
    const Track& track,
    const Eigen::Vector3d& point_mm,
    const std::map<std::string, Pose>& poses,
    const std::map<std::string, Intrinsics>& intrinsics,
    double* max_residual,
    double* mean_residual,
    int* residual_count,
    std::map<std::string, double>* frame_max_residual,
    std::map<std::string, int>* frame_observation_count)
{
    *max_residual = 0.0;
    double sum_residual = 0.0;
    *residual_count = 0;
    for (const auto& observation : track.observations) {
        const auto pose_it = poses.find(observation.frame_id);
        const auto intrinsics_it = intrinsics.find(observation.frame_id);
        if (pose_it == poses.end() || intrinsics_it == intrinsics.end()) {
            continue;
        }
        const double residual = reprojection_residual_px(
            point_mm,
            observation,
            pose_it->second,
            intrinsics_it->second
        );
        if (!std::isfinite(residual)) {
            continue;
        }
        *max_residual = std::max(*max_residual, residual);
        sum_residual += residual;
        *residual_count += 1;
        if (frame_max_residual != nullptr) {
            (*frame_max_residual)[observation.frame_id] =
                std::max((*frame_max_residual)[observation.frame_id], residual);
        }
        if (frame_observation_count != nullptr) {
            (*frame_observation_count)[observation.frame_id] += 1;
        }
    }
    *mean_residual = *residual_count == 0 ? 0.0 : sum_residual / static_cast<double>(*residual_count);
}

void prune_extreme_outlier_tracks(
    const std::vector<Track>& tracks,
    const std::map<std::string, Pose>& poses,
    const std::map<std::string, Intrinsics>& intrinsics,
    double outlier_gate_px,
    json* rejected_track_reports,
    std::map<std::string, Eigen::Vector3d>* points_by_track)
{
    std::vector<std::string> rejected_track_ids;
    std::map<std::string, json> rejection_reports;
    for (const auto& point_item : *points_by_track) {
        const Track* source_track = nullptr;
        for (const Track& track : tracks) {
            if (track.track_id == point_item.first) {
                source_track = &track;
                break;
            }
        }
        if (source_track == nullptr) {
            rejected_track_ids.push_back(point_item.first);
            rejection_reports[point_item.first] = {
                {"track_id", point_item.first},
                {"reason", "source_track_missing"},
                {"residual_px", nullptr},
                {"mean_residual_px", nullptr},
                {"residual_count", 0},
                {"outlier_gate_px", outlier_gate_px}
            };
            continue;
        }
        double max_residual = 0.0;
        double mean_residual = 0.0;
        int residual_count = 0;
        measure_point_residuals(
            *source_track,
            point_item.second,
            poses,
            intrinsics,
            &max_residual,
            &mean_residual,
            &residual_count,
            nullptr,
            nullptr
        );
        if (residual_count < 2 || max_residual > outlier_gate_px) {
            rejected_track_ids.push_back(point_item.first);
            rejection_reports[point_item.first] = {
                {"track_id", point_item.first},
                {"reason", residual_count < 2 ? "insufficient_residual_support" : "iterative_outlier_pruned"},
                {"residual_px", residual_count == 0 ? json(nullptr) : json(max_residual)},
                {"mean_residual_px", residual_count == 0 ? json(nullptr) : json(mean_residual)},
                {"residual_count", residual_count},
                {"outlier_gate_px", outlier_gate_px}
            };
        }
    }
    for (const std::string& track_id : rejected_track_ids) {
        points_by_track->erase(track_id);
        if (rejected_track_reports != nullptr && rejection_reports.count(track_id) > 0) {
            rejected_track_reports->push_back(rejection_reports[track_id]);
        }
    }
}

void refine_extrinsics_once(
    const std::vector<Track>& tracks,
    const std::map<std::string, Intrinsics>& intrinsics,
    const std::map<std::string, Eigen::Vector3d>& points_by_track,
    const std::string& anchor_frame,
    double robust_delta_px,
    std::map<std::string, Pose>* poses)
{
    for (auto& pose_item : *poses) {
        Pose& pose = pose_item.second;
        if (pose.frame_id == anchor_frame) {
            continue;
        }
        Eigen::Matrix<double, 6, 6> normal = Eigen::Matrix<double, 6, 6>::Zero();
        Eigen::Matrix<double, 6, 1> rhs = Eigen::Matrix<double, 6, 1>::Zero();
        int residual_count = 0;
        for (const Track& track : tracks) {
            const auto point_it = points_by_track.find(track.track_id);
            if (point_it == points_by_track.end()) {
                continue;
            }
            for (const Observation& observation : track.observations) {
                if (observation.frame_id != pose.frame_id) {
                    continue;
                }
                const auto intrinsics_it = intrinsics.find(observation.frame_id);
                if (intrinsics_it == intrinsics.end()) {
                    continue;
                }
                Eigen::Vector2d residual;
                Eigen::Matrix<double, 2, 3> translation_jacobian;
                Eigen::Matrix<double, 2, 3> rotation_jacobian;
                if (!projection_residual_and_jacobians(
                        point_it->second,
                        observation,
                        pose,
                        intrinsics_it->second,
                        &residual,
                        nullptr,
                        &translation_jacobian,
                        &rotation_jacobian)) {
                    continue;
                }
                Eigen::Matrix<double, 2, 6> jacobian;
                jacobian.block<2, 3>(0, 0) = rotation_jacobian;
                jacobian.block<2, 3>(0, 3) = translation_jacobian;
                const double weight = huber_weight(residual.norm(), robust_delta_px);
                normal += weight * jacobian.transpose() * jacobian;
                rhs += weight * jacobian.transpose() * residual;
                residual_count += 1;
            }
        }
        if (residual_count < 4) {
            continue;
        }
        normal += Eigen::Matrix<double, 6, 6>::Identity() * 1e-6;
        const Eigen::Matrix<double, 6, 1> delta = -normal.ldlt().solve(rhs);
        if (!delta.allFinite()) {
            continue;
        }
        Eigen::Vector3d delta_rotation = delta.block<3, 1>(0, 0);
        Eigen::Vector3d delta_translation = delta.block<3, 1>(3, 0);
        const double max_rotation_rad = 0.05;
        const double max_translation_mm = 25.0;
        const double rotation_norm = delta_rotation.norm();
        const double translation_norm = delta_translation.norm();
        if (rotation_norm > max_rotation_rad) {
            delta_rotation *= max_rotation_rad / rotation_norm;
        }
        if (translation_norm > max_translation_mm) {
            delta_translation *= max_translation_mm / translation_norm;
        }
        pose.rotation = orthonormalized(exp_so3(delta_rotation) * pose.rotation);
        pose.translation_mm += delta_translation;
    }
}

void refine_marker_extrinsics_once(
    const std::vector<MarkerCornerObservation>& marker_observations,
    const std::map<std::string, Intrinsics>& intrinsics,
    const std::string& anchor_frame,
    double robust_delta_px,
    std::map<std::string, Pose>* poses)
{
    for (auto& pose_item : *poses) {
        Pose& pose = pose_item.second;
        if (pose.frame_id == anchor_frame) {
            continue;
        }
        const auto intrinsics_it = intrinsics.find(pose.frame_id);
        if (intrinsics_it == intrinsics.end()) {
            continue;
        }
        Eigen::Matrix<double, 6, 6> normal = Eigen::Matrix<double, 6, 6>::Zero();
        Eigen::Matrix<double, 6, 1> rhs = Eigen::Matrix<double, 6, 1>::Zero();
        int residual_count = 0;
        for (const MarkerCornerObservation& observation : marker_observations) {
            if (observation.frame_id != pose.frame_id) {
                continue;
            }
            Eigen::Vector2d residual;
            Eigen::Matrix<double, 2, 3> translation_jacobian;
            Eigen::Matrix<double, 2, 3> rotation_jacobian;
            Observation image_observation;
            image_observation.frame_id = observation.frame_id;
            image_observation.x = observation.x;
            image_observation.y = observation.y;
            if (!projection_residual_and_jacobians(
                    observation.world_xyz_mm,
                    image_observation,
                    pose,
                    intrinsics_it->second,
                    &residual,
                    nullptr,
                    &translation_jacobian,
                    &rotation_jacobian)) {
                continue;
            }
            Eigen::Matrix<double, 2, 6> jacobian;
            jacobian.block<2, 3>(0, 0) = rotation_jacobian;
            jacobian.block<2, 3>(0, 3) = translation_jacobian;
            const double weight = huber_weight(residual.norm(), robust_delta_px);
            normal += weight * jacobian.transpose() * jacobian;
            rhs += weight * jacobian.transpose() * residual;
            residual_count += 1;
        }
        if (residual_count < 4) {
            continue;
        }
        normal += Eigen::Matrix<double, 6, 6>::Identity() * 1e-6;
        const Eigen::Matrix<double, 6, 1> delta = -normal.ldlt().solve(rhs);
        if (!delta.allFinite()) {
            continue;
        }
        Eigen::Vector3d delta_rotation = delta.block<3, 1>(0, 0);
        Eigen::Vector3d delta_translation = delta.block<3, 1>(3, 0);
        const double max_rotation_rad = 0.03;
        const double max_translation_mm = 15.0;
        const double rotation_norm = delta_rotation.norm();
        const double translation_norm = delta_translation.norm();
        if (rotation_norm > max_rotation_rad) {
            delta_rotation *= max_rotation_rad / rotation_norm;
        }
        if (translation_norm > max_translation_mm) {
            delta_translation *= max_translation_mm / translation_norm;
        }
        pose.rotation = orthonormalized(exp_so3(delta_rotation) * pose.rotation);
        pose.translation_mm += delta_translation;
    }
}

json marker_corner_residuals_json(
    const std::vector<MarkerCornerObservation>& marker_observations,
    const std::map<std::string, Pose>& poses,
    const std::map<std::string, Intrinsics>& intrinsics,
    double* max_residual_px,
    double* mean_residual_px,
    int* residual_count,
    std::map<std::string, double>* frame_marker_max_residual = nullptr,
    std::map<std::string, int>* frame_marker_observation_count = nullptr)
{
    json output = json::array();
    *max_residual_px = 0.0;
    *mean_residual_px = 0.0;
    *residual_count = 0;
    double sum_residual = 0.0;
    for (const MarkerCornerObservation& observation : marker_observations) {
        const auto pose_it = poses.find(observation.frame_id);
        const auto intrinsics_it = intrinsics.find(observation.frame_id);
        if (pose_it == poses.end() || intrinsics_it == intrinsics.end()) {
            continue;
        }
        Observation image_observation;
        image_observation.frame_id = observation.frame_id;
        image_observation.x = observation.x;
        image_observation.y = observation.y;
        const double residual = reprojection_residual_px(
            observation.world_xyz_mm,
            image_observation,
            pose_it->second,
            intrinsics_it->second
        );
        if (!std::isfinite(residual)) {
            continue;
        }
        *max_residual_px = std::max(*max_residual_px, residual);
        sum_residual += residual;
        *residual_count += 1;
        if (frame_marker_max_residual != nullptr) {
            (*frame_marker_max_residual)[observation.frame_id] =
                std::max((*frame_marker_max_residual)[observation.frame_id], residual);
        }
        if (frame_marker_observation_count != nullptr) {
            (*frame_marker_observation_count)[observation.frame_id] += 1;
        }
        output.push_back({
            {"frame_id", observation.frame_id},
            {"marker_id", observation.marker_id},
            {"corner_index", observation.corner_index},
            {"residual_px", residual}
        });
    }
    *mean_residual_px = *residual_count == 0 ? 0.0 : sum_residual / static_cast<double>(*residual_count);
    return output;
}

json frame_uncertainty_json(
    const std::map<std::string, Pose>& poses,
    const std::map<std::string, int>& frame_feature_observation_count,
    const std::map<std::string, double>& frame_feature_max_residual,
    const std::map<std::string, int>& frame_marker_observation_count,
    const std::map<std::string, double>& frame_marker_max_residual,
    double max_track_residual_px,
    double max_marker_residual_px)
{
    json output = json::array();
    for (const auto& item : poses) {
        const std::string& frame_id = item.first;
        const int feature_count = frame_feature_observation_count.count(frame_id)
            ? frame_feature_observation_count.at(frame_id)
            : 0;
        const int marker_count = frame_marker_observation_count.count(frame_id)
            ? frame_marker_observation_count.at(frame_id)
            : 0;
        const double feature_residual = frame_feature_max_residual.count(frame_id)
            ? frame_feature_max_residual.at(frame_id)
            : 0.0;
        const double marker_residual = frame_marker_max_residual.count(frame_id)
            ? frame_marker_max_residual.at(frame_id)
            : 0.0;
        const double feature_score = feature_count <= 0
            ? 0.0
            : std::max(0.0, 1.0 - feature_residual / std::max(max_track_residual_px, 1e-6));
        const double marker_score = marker_count <= 0
            ? 0.0
            : std::max(0.0, 1.0 - marker_residual / std::max(max_marker_residual_px, 1e-6));
        const double support_score = std::min(1.0, static_cast<double>(feature_count) / 12.0) * 0.55
            + std::min(1.0, static_cast<double>(marker_count) / 4.0) * 0.45;
        const double confidence = std::max(0.0, std::min(1.0, support_score * (0.5 + 0.25 * feature_score + 0.25 * marker_score)));
        output.push_back({
            {"frame_id", frame_id},
            {"feature_observation_count", feature_count},
            {"marker_corner_observation_count", marker_count},
            {"feature_residual_px", feature_count <= 0 ? json(nullptr) : json(feature_residual)},
            {"marker_corner_residual_px", marker_count <= 0 ? json(nullptr) : json(marker_residual)},
            {"confidence", confidence},
            {"uncertainty_class", confidence >= 0.85 ? "measured_high" : (confidence >= 0.65 ? "measured_low" : "weak")}
        });
    }
    return output;
}

json pose_conditioning_json(
    const std::vector<Track>& tracks,
    const std::map<std::string, Eigen::Vector3d>& points_by_track,
    const std::vector<MarkerCornerObservation>& marker_observations,
    const std::map<std::string, Pose>& poses,
    const std::map<std::string, Intrinsics>& intrinsics,
    double robust_delta_px)
{
    json output = json::array();
    for (const auto& pose_item : poses) {
        const Pose& pose = pose_item.second;
        const auto intrinsics_it = intrinsics.find(pose.frame_id);
        if (intrinsics_it == intrinsics.end()) {
            output.push_back({
                {"frame_id", pose.frame_id},
                {"residual_block_count", 0},
                {"condition_number", nullptr},
                {"rotation_sigma_rad", nullptr},
                {"translation_sigma_mm", nullptr},
                {"conditioning_class", "missing_intrinsics"}
            });
            continue;
        }

        Eigen::Matrix<double, 6, 6> normal = Eigen::Matrix<double, 6, 6>::Zero();
        int residual_blocks = 0;
        for (const Track& track : tracks) {
            const auto point_it = points_by_track.find(track.track_id);
            if (point_it == points_by_track.end()) {
                continue;
            }
            for (const Observation& observation : track.observations) {
                if (observation.frame_id != pose.frame_id) {
                    continue;
                }
                Eigen::Vector2d residual;
                Eigen::Matrix<double, 2, 3> translation_jacobian;
                Eigen::Matrix<double, 2, 3> rotation_jacobian;
                if (!projection_residual_and_jacobians(
                        point_it->second,
                        observation,
                        pose,
                        intrinsics_it->second,
                        &residual,
                        nullptr,
                        &translation_jacobian,
                        &rotation_jacobian)) {
                    continue;
                }
                Eigen::Matrix<double, 2, 6> jacobian;
                jacobian.block<2, 3>(0, 0) = rotation_jacobian;
                jacobian.block<2, 3>(0, 3) = translation_jacobian;
                const double weight = huber_weight(residual.norm(), robust_delta_px);
                normal += weight * jacobian.transpose() * jacobian;
                residual_blocks += 1;
            }
        }

        for (const MarkerCornerObservation& observation : marker_observations) {
            if (observation.frame_id != pose.frame_id) {
                continue;
            }
            Observation image_observation;
            image_observation.frame_id = observation.frame_id;
            image_observation.x = observation.x;
            image_observation.y = observation.y;
            Eigen::Vector2d residual;
            Eigen::Matrix<double, 2, 3> translation_jacobian;
            Eigen::Matrix<double, 2, 3> rotation_jacobian;
            if (!projection_residual_and_jacobians(
                    observation.world_xyz_mm,
                    image_observation,
                    pose,
                    intrinsics_it->second,
                    &residual,
                    nullptr,
                    &translation_jacobian,
                    &rotation_jacobian)) {
                continue;
            }
            Eigen::Matrix<double, 2, 6> jacobian;
            jacobian.block<2, 3>(0, 0) = rotation_jacobian;
            jacobian.block<2, 3>(0, 3) = translation_jacobian;
            const double weight = huber_weight(residual.norm(), robust_delta_px);
            normal += weight * jacobian.transpose() * jacobian;
            residual_blocks += 1;
        }

        normal += Eigen::Matrix<double, 6, 6>::Identity() * 1e-9;
        Eigen::SelfAdjointEigenSolver<Eigen::Matrix<double, 6, 6>> solver(normal);
        if (residual_blocks < 4 || solver.info() != Eigen::Success) {
            output.push_back({
                {"frame_id", pose.frame_id},
                {"residual_block_count", residual_blocks},
                {"condition_number", nullptr},
                {"rotation_sigma_rad", nullptr},
                {"translation_sigma_mm", nullptr},
                {"conditioning_class", "underdetermined"}
            });
            continue;
        }

        const auto eigenvalues = solver.eigenvalues();
        const double min_eigen = std::max(eigenvalues.minCoeff(), 1e-12);
        const double max_eigen = std::max(eigenvalues.maxCoeff(), min_eigen);
        const double condition_number = max_eigen / min_eigen;
        Eigen::Matrix<double, 6, 6> covariance =
            (normal + Eigen::Matrix<double, 6, 6>::Identity() * 1e-6).ldlt().solve(Eigen::Matrix<double, 6, 6>::Identity());
        double rotation_sigma = 0.0;
        double translation_sigma = 0.0;
        for (int index = 0; index < 3; ++index) {
            rotation_sigma = std::max(rotation_sigma, covariance(index, index));
            translation_sigma = std::max(translation_sigma, covariance(index + 3, index + 3));
        }
        rotation_sigma = std::sqrt(std::max(rotation_sigma, 0.0));
        translation_sigma = std::sqrt(std::max(translation_sigma, 0.0));
        const std::string conditioning_class = condition_number < 1e6
            ? "well_conditioned"
            : (condition_number < 1e9 ? "weakly_conditioned" : "ill_conditioned");
        output.push_back({
            {"frame_id", pose.frame_id},
            {"residual_block_count", residual_blocks},
            {"condition_number", condition_number},
            {"rotation_sigma_rad", rotation_sigma},
            {"translation_sigma_mm", translation_sigma},
            {"conditioning_class", conditioning_class}
        });
    }
    return output;
}

json fail_response(const std::string& status, const std::string& detail)
{
    return {
        {"success", false},
        {"status", status},
        {"detail", detail},
        {"solver_name", kSolverName},
        {"backend_name", kBackendName},
        {"ceres_linked", false},
        {"optimizer_linked", true},
        {"optimized_camera_poses", json::array()},
        {"optimized_sparse_points", json::array()},
        {"per_frame_residuals", json::array()},
        {"per_track_residuals", json::array()},
        {"marker_residual_px", nullptr},
        {"scale_residual_percent", nullptr},
        {"rejected_observations", json::array()},
        {"rejected_tracks", json::array()},
        {"solver_iterations", 0},
        {"solver_runtime_ms", 0}
    };
}

json optimize_request(const json& request)
{
    const auto started = std::chrono::steady_clock::now();
    const std::map<std::string, Intrinsics> intrinsics = parse_intrinsics(request);
    std::map<std::string, Pose> poses = parse_poses(request);
    const std::vector<Track> tracks = parse_tracks(request);
    const std::vector<MarkerCornerObservation> marker_observations = parse_marker_corner_observations(request);
    const auto residual_limits = request.value("residual_limits", json::object());
    const auto solver_limits = request.value("solver_limits", json::object());
    const auto marker_scale_constraint = request.value("marker_scale_constraint", json::object());
    const auto bundle_adjustment_preflight = request.value("bundle_adjustment_preflight", json::object());
    const double max_track_residual_px = residual_limits.value("max_track_residual_px", 2.5);
    const double max_marker_residual_px = residual_limits.value("max_marker_residual_px", 3.0);
    const double max_scale_residual_percent = residual_limits.value("max_scale_residual_percent", 1.5);
    const int min_marker_corner_observation_count = residual_limits.value("min_marker_corner_observation_count", 16);
    const bool require_verified_marker_mat = residual_limits.value("require_verified_marker_mat", true);
    const int requested_iterations = solver_limits.value("max_iterations", 8);
    const int solver_iterations = std::max(1, std::min(requested_iterations, 8));
    const double robust_delta_px = std::max(0.5, std::min(max_track_residual_px, 4.0));
    const double outlier_gate_px = std::max(max_track_residual_px * 3.0, max_track_residual_px + 2.0);
    const bool verified_marker_mat = marker_scale_constraint.value("verified_marker_mat", false);
    double scale_confidence = 0.0;
    nullable_double(marker_scale_constraint, "scale_confidence", &scale_confidence);
    double marker_residual_px = 0.0;
    const bool has_marker_residual = nullable_double(bundle_adjustment_preflight, "marker_residual_px", &marker_residual_px)
        || nullable_double(marker_scale_constraint, "marker_reprojection_error_px", &marker_residual_px);
    double scale_residual_percent = 0.0;
    const bool has_scale_residual = nullable_double(bundle_adjustment_preflight, "scale_residual_percent", &scale_residual_percent);

    if (poses.size() < 2) {
        return fail_response("not_enough_candidate_poses", "At least two candidate camera poses are required.");
    }
    if (tracks.empty()) {
        return fail_response("no_feature_tracks", "At least one multi-view feature track is required.");
    }
    if (static_cast<int>(marker_observations.size()) < min_marker_corner_observation_count) {
        return fail_response("marker_corner_observations_missing", "Native metric optimization requires enough marker-corner residual observations.");
    }
    if (require_verified_marker_mat && !verified_marker_mat) {
        return fail_response("marker_scale_unverified", "Native metric optimization requires verified marker-mat scale evidence.");
    }
    if (require_verified_marker_mat && scale_confidence < 0.85) {
        return fail_response("scale_confidence_low", "Native metric optimization requires marker scale confidence of at least 0.85.");
    }
    if (!has_marker_residual) {
        return fail_response("marker_residual_missing", "Native metric optimization requires marker reprojection residual evidence.");
    }
    if (marker_residual_px < 0.0 || marker_residual_px > max_marker_residual_px) {
        return fail_response("marker_residual_high", "Native metric optimization rejected high marker reprojection residual.");
    }
    if (!has_scale_residual) {
        return fail_response("scale_residual_missing", "Native metric optimization requires scale residual evidence.");
    }
    if (scale_residual_percent < 0.0 || scale_residual_percent > max_scale_residual_percent) {
        return fail_response("scale_residual_high", "Native metric optimization rejected high scale residual.");
    }

    std::map<std::string, Eigen::Vector3d> points_by_track;
    json pruned_track_reports = json::array();
    std::vector<OptimizedPoint> point_results;
    point_results.reserve(tracks.size());
    for (const Track& track : tracks) {
        Eigen::Vector3d point_mm = Eigen::Vector3d::Zero();
        OptimizedPoint point;
        point.track_id = track.track_id;
        if (!triangulate_track(track, poses, &point_mm)) {
            point.accepted = false;
            point.rejection_reason = "triangulation_degenerate";
            point_results.push_back(point);
            continue;
        }
        points_by_track[track.track_id] = point_mm;
        point.xyz_mm = point_mm;
        point_results.push_back(point);
    }

    const std::string anchor_frame = poses.begin()->first;
    for (int iteration = 0; iteration < solver_iterations; ++iteration) {
        for (const Track& track : tracks) {
            auto point_it = points_by_track.find(track.track_id);
            if (point_it == points_by_track.end()) {
                continue;
            }
            refine_point_gauss_newton(
                track,
                poses,
                intrinsics,
                2,
                robust_delta_px,
                &point_it->second
            );
        }
        prune_extreme_outlier_tracks(
            tracks,
            poses,
            intrinsics,
            outlier_gate_px,
            &pruned_track_reports,
            &points_by_track
        );
        refine_extrinsics_once(tracks, intrinsics, points_by_track, anchor_frame, robust_delta_px, &poses);
        refine_marker_extrinsics_once(marker_observations, intrinsics, anchor_frame, robust_delta_px, &poses);
    }

    json optimized_points = json::array();
    json per_track_residuals = json::array();
    json rejected_tracks = json::array();
    json rejected_observations = json::array();
    std::map<std::string, double> frame_max_residual;
    std::map<std::string, int> frame_observation_count;

    int point_index = 1;
    for (OptimizedPoint& point : point_results) {
        const Track* source_track = nullptr;
        for (const Track& track : tracks) {
            if (track.track_id == point.track_id) {
                source_track = &track;
                break;
            }
        }
        const auto point_it = points_by_track.find(point.track_id);
        if (source_track == nullptr || point_it == points_by_track.end()) {
            rejected_tracks.push_back(point.track_id);
            std::string reason = point.rejection_reason.empty() ? "point_missing" : point.rejection_reason;
            for (const auto& report : pruned_track_reports) {
                if (report.value("track_id", "") == point.track_id) {
                    reason = report.value("reason", reason);
                    break;
                }
            }
            per_track_residuals.push_back({
                {"track_id", point.track_id},
                {"observation_count", source_track == nullptr ? 0 : source_track->observations.size()},
                {"residual_px", nullptr},
                {"accepted", false},
                {"reason", reason}
            });
            continue;
        }
        point.xyz_mm = point_it->second;
        measure_point_residuals(
            *source_track,
            point.xyz_mm,
            poses,
            intrinsics,
            &point.max_residual_px,
            &point.mean_residual_px,
            &point.residual_count,
            &frame_max_residual,
            &frame_observation_count
        );
        if (point.residual_count < 2 || point.max_residual_px > max_track_residual_px) {
            point.accepted = false;
            point.rejection_reason = point.residual_count < 2 ? "insufficient_residual_support" : "residual_high";
            rejected_tracks.push_back(point.track_id);
            per_track_residuals.push_back({
                {"track_id", point.track_id},
                {"observation_count", source_track->observations.size()},
                {"residual_px", point.residual_count == 0 ? json(nullptr) : json(point.max_residual_px)},
                {"accepted", false},
                {"reason", point.rejection_reason}
            });
            continue;
        }
        point.accepted = true;
        point.point_id = "opt_point_" + std::to_string(point_index++);
        optimized_points.push_back({
            {"point_id", point.point_id},
            {"source_track_id", point.track_id},
            {"xyz_mm", {point.xyz_mm.x(), point.xyz_mm.y(), point.xyz_mm.z()}},
            {"reprojection_residual_px", point.max_residual_px},
            {"provenance_class", point.max_residual_px <= max_track_residual_px * 0.5 ? "measured_high" : "measured_low"}
        });
        per_track_residuals.push_back({
            {"track_id", point.track_id},
            {"observation_count", source_track->observations.size()},
            {"residual_px", point.max_residual_px},
            {"mean_residual_px", point.mean_residual_px},
            {"accepted", true}
        });
    }

    json optimized_poses = json::array();
    for (const auto& item : poses) {
        const Pose& pose = item.second;
        const double residual = frame_max_residual.count(pose.frame_id) ? frame_max_residual[pose.frame_id] : 0.0;
        optimized_poses.push_back({
            {"frame_id", pose.frame_id},
            {"rotation_row_major", {
                pose.rotation(0, 0), pose.rotation(0, 1), pose.rotation(0, 2),
                pose.rotation(1, 0), pose.rotation(1, 1), pose.rotation(1, 2),
                pose.rotation(2, 0), pose.rotation(2, 1), pose.rotation(2, 2)
            }},
            {"translation_mm", {pose.translation_mm.x(), pose.translation_mm.y(), pose.translation_mm.z()}},
            {"residual_px", residual},
            {"metric_validated", !optimized_points.empty() && frame_observation_count[pose.frame_id] > 0}
        });
    }

    json per_frame_residuals = json::array();
    for (const auto& item : poses) {
        const std::string& frame_id = item.first;
        per_frame_residuals.push_back({
            {"frame_id", frame_id},
            {"observation_count", frame_observation_count[frame_id]},
            {"residual_px", frame_max_residual.count(frame_id) ? frame_max_residual[frame_id] : 0.0}
        });
    }

    double marker_corner_max_residual_px = 0.0;
    double marker_corner_mean_residual_px = 0.0;
    int marker_corner_residual_count = 0;
    std::map<std::string, double> frame_marker_max_residual;
    std::map<std::string, int> frame_marker_observation_count;
    json marker_corner_residuals = marker_corner_residuals_json(
        marker_observations,
        poses,
        intrinsics,
        &marker_corner_max_residual_px,
        &marker_corner_mean_residual_px,
        &marker_corner_residual_count,
        &frame_marker_max_residual,
        &frame_marker_observation_count
    );
    if (marker_corner_residual_count < min_marker_corner_observation_count) {
        return fail_response("marker_corner_residuals_insufficient", "Native metric optimization could not evaluate enough marker-corner residuals.");
    }
    if (marker_corner_max_residual_px > max_marker_residual_px) {
        return fail_response("marker_corner_residual_high", "Native metric optimization rejected high marker-corner residual after extrinsic refinement.");
    }
    for (const auto& residual : marker_corner_residuals) {
        if (residual.value("residual_px", 0.0) > max_marker_residual_px) {
            rejected_observations.push_back({
                {"type", "marker_corner"},
                {"frame_id", residual.value("frame_id", "")},
                {"marker_id", residual.value("marker_id", "")},
                {"corner_index", residual.value("corner_index", 0)},
                {"residual_px", residual.value("residual_px", 0.0)},
                {"reason", "marker_corner_residual_high"}
            });
        }
    }
    const json uncertainty_by_frame = frame_uncertainty_json(
        poses,
        frame_observation_count,
        frame_max_residual,
        frame_marker_observation_count,
        frame_marker_max_residual,
        max_track_residual_px,
        max_marker_residual_px
    );
    const json pose_conditioning = pose_conditioning_json(
        tracks,
        points_by_track,
        marker_observations,
        poses,
        intrinsics,
        robust_delta_px
    );

    const auto finished = std::chrono::steady_clock::now();
    const auto runtime_ms = std::chrono::duration_cast<std::chrono::milliseconds>(finished - started).count();
    return {
        {"success", !optimized_points.empty()},
        {"status", optimized_points.empty() ? "no_accepted_sparse_points" : "ok"},
        {"detail", optimized_points.empty()
            ? "Native Eigen optimizer ran, but no sparse points passed residual gates."
            : "Native Eigen optimizer produced candidate optimized poses and sparse points. Kotlin metric gates still decide acceptance."},
        {"solver_name", kSolverName},
        {"backend_name", kBackendName},
        {"ceres_linked", false},
        {"optimizer_linked", true},
        {"optimized_camera_poses", optimized_poses},
        {"optimized_sparse_points", optimized_points},
        {"per_frame_residuals", per_frame_residuals},
        {"per_track_residuals", per_track_residuals},
        {"marker_residual_px", has_marker_residual ? json(marker_residual_px) : json(nullptr)},
        {"scale_residual_percent", has_scale_residual ? json(scale_residual_percent) : json(nullptr)},
        {"marker_scale_constraint_applied", verified_marker_mat},
        {"marker_corner_residuals", marker_corner_residuals},
        {"marker_corner_residual_count", marker_corner_residual_count},
        {"marker_corner_residual_max_px", marker_corner_max_residual_px},
        {"marker_corner_residual_mean_px", marker_corner_mean_residual_px},
        {"marker_corner_residual_blocks_enabled", true},
        {"uncertainty_by_frame", uncertainty_by_frame},
        {"pose_conditioning", pose_conditioning},
        {"pruned_track_reports", pruned_track_reports},
        {"scale_confidence", scale_confidence},
        {"rotation_refinement_enabled", true},
        {"translation_refinement_enabled", true},
        {"sparse_point_refinement_enabled", true},
        {"robust_loss", "huber"},
        {"robust_delta_px", robust_delta_px},
        {"outlier_gate_px", outlier_gate_px},
        {"iterative_outlier_pruning_enabled", true},
        {"rejected_observations", rejected_observations},
        {"rejected_tracks", rejected_tracks},
        {"solver_iterations", solver_iterations},
        {"solver_runtime_ms", runtime_ms}
    };
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_scanner_ScannerNativePoseOptimizerBridge_nativeStatusJson(JNIEnv* env, jobject)
{
    const json response = {
        {"available", true},
        {"status", "ready_eigen_extrinsic"},
        {"detail", "Eigen-backed scanner pose optimizer is linked. It performs conservative fixed-intrinsics sparse point, camera rotation, camera translation, Huber-weighted residuals, iterative outlier pruning, and marker-scale gate enforcement; full Ceres bundle adjustment is still not linked."},
        {"solver_name", kSolverName},
        {"backend_name", kBackendName},
        {"ceres_linked", false},
        {"optimizer_linked", true}
    };
    return env->NewStringUTF(response.dump().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_scanner_ScannerNativePoseOptimizerBridge_nativeOptimizeJson(
    JNIEnv* env,
    jobject,
    jstring request_json)
{
    const char* request_chars = env->GetStringUTFChars(request_json, nullptr);
    if (request_chars == nullptr) {
        const json response = fail_response("request_unavailable", "JNI request string was unavailable.");
        return env->NewStringUTF(response.dump().c_str());
    }
    const std::string request_text(request_chars);
    env->ReleaseStringUTFChars(request_json, request_chars);

    json response;
    try {
        response = optimize_request(json::parse(request_text));
    } catch (const std::exception& error) {
        response = fail_response("request_parse_failed", error.what());
    }
    return env->NewStringUTF(response.dump().c_str());
}
