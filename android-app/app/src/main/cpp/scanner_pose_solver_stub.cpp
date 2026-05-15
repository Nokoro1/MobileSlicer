#include <jni.h>

#if defined(MOBILE_SLICER_HAS_OPENCV) && MOBILE_SLICER_HAS_OPENCV
#include <opencv2/calib3d.hpp>
#include <opencv2/core.hpp>
#include <sstream>
#include <string>
#include <vector>
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_scanner_ScannerNativePoseSolverBridge_nativeStatusJson(JNIEnv* env, jobject)
{
#if defined(MOBILE_SLICER_HAS_OPENCV) && MOBILE_SLICER_HAS_OPENCV
    return env->NewStringUTF(
        "{\"available\":true,"
        "\"status\":\"ready\","
        "\"detail\":\"OpenCV-backed metric pose solver bridge is linked. Bundle adjustment implementation is still gated by Kotlin solver checks.\","
        "\"solver_name\":\"native_opencv_metric_pose_solver\","
        "\"opencv_linked\":true}"
    );
#else
    return env->NewStringUTF(
        "{\"available\":false,"
        "\"status\":\"opencv_unavailable\","
        "\"detail\":\"OpenCV-backed metric pose solver is not linked in this build. Dense reconstruction and metric mesh export must remain blocked.\","
        "\"solver_name\":\"native_opencv_metric_pose_solver\","
        "\"opencv_linked\":false}"
    );
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_scanner_ScannerNativePoseSolverBridge_nativeSolveRelativePairJson(
    JNIEnv* env,
    jobject,
    jdoubleArray points_a,
    jdoubleArray points_b,
    jdouble fx_a,
    jdouble fy_a,
    jdouble cx_a,
    jdouble cy_a,
    jdouble fx_b,
    jdouble fy_b,
    jdouble cx_b,
    jdouble cy_b,
    jint min_inliers,
    jdouble max_reprojection_error_px)
{
#if defined(MOBILE_SLICER_HAS_OPENCV) && MOBILE_SLICER_HAS_OPENCV
    const jsize length_a = env->GetArrayLength(points_a);
    const jsize length_b = env->GetArrayLength(points_b);
    if (length_a != length_b || length_a < 10 || length_a % 2 != 0) {
        return env->NewStringUTF(
            "{\"success\":false,"
            "\"status\":\"invalid_correspondence_input\","
            "\"detail\":\"Relative pose solving requires equal point arrays with at least five 2D correspondences.\","
            "\"inlier_count\":0,"
            "\"inlier_ratio\":0.0,"
            "\"rotation_row_major\":[],"
            "\"translation_unit\":[]}"
        );
    }

    std::vector<double> buffer_a(static_cast<size_t>(length_a));
    std::vector<double> buffer_b(static_cast<size_t>(length_b));
    env->GetDoubleArrayRegion(points_a, 0, length_a, buffer_a.data());
    env->GetDoubleArrayRegion(points_b, 0, length_b, buffer_b.data());

    std::vector<cv::Point2d> image_points_a;
    std::vector<cv::Point2d> image_points_b;
    image_points_a.reserve(static_cast<size_t>(length_a / 2));
    image_points_b.reserve(static_cast<size_t>(length_b / 2));
    for (jsize i = 0; i < length_a; i += 2) {
        image_points_a.emplace_back(buffer_a[static_cast<size_t>(i)], buffer_a[static_cast<size_t>(i + 1)]);
        image_points_b.emplace_back(buffer_b[static_cast<size_t>(i)], buffer_b[static_cast<size_t>(i + 1)]);
    }

    const double fx = (fx_a + fx_b) * 0.5;
    const double fy = (fy_a + fy_b) * 0.5;
    const double cx = (cx_a + cx_b) * 0.5;
    const double cy = (cy_a + cy_b) * 0.5;
    const cv::Mat camera_matrix = (cv::Mat_<double>(3, 3) << fx, 0.0, cx, 0.0, fy, cy, 0.0, 0.0, 1.0);

    cv::Mat essential_mask;
    cv::Mat essential = cv::findEssentialMat(
        image_points_a,
        image_points_b,
        camera_matrix,
        cv::RANSAC,
        0.999,
        max_reprojection_error_px,
        essential_mask
    );
    if (essential.empty()) {
        return env->NewStringUTF(
            "{\"success\":false,"
            "\"status\":\"essential_matrix_failed\","
            "\"detail\":\"OpenCV could not estimate an essential matrix from the selected pair.\","
            "\"inlier_count\":0,"
            "\"inlier_ratio\":0.0,"
            "\"rotation_row_major\":[],"
            "\"translation_unit\":[]}"
        );
    }

    cv::Mat rotation;
    cv::Mat translation;
    cv::Mat recover_mask;
    const int inliers = cv::recoverPose(
        essential,
        image_points_a,
        image_points_b,
        camera_matrix,
        rotation,
        translation,
        recover_mask
    );
    const int total = static_cast<int>(image_points_a.size());
    if (inliers < min_inliers) {
        std::ostringstream failed;
        failed << "{\"success\":false,"
               << "\"status\":\"relative_pose_inliers_low\","
               << "\"detail\":\"OpenCV recovered a relative pose, but inlier support is below the configured threshold.\","
               << "\"inlier_count\":" << inliers << ","
               << "\"inlier_ratio\":" << (total > 0 ? static_cast<double>(inliers) / static_cast<double>(total) : 0.0) << ","
               << "\"rotation_row_major\":[],"
               << "\"translation_unit\":[]}";
        return env->NewStringUTF(failed.str().c_str());
    }

    std::ostringstream result;
    result << "{\"success\":true,"
           << "\"status\":\"relative_pose_recovered\","
           << "\"detail\":\"OpenCV recovered a calibrated relative pose for the selected frame pair. Translation is unit length and not yet metric scale.\","
           << "\"inlier_count\":" << inliers << ","
           << "\"inlier_ratio\":" << (total > 0 ? static_cast<double>(inliers) / static_cast<double>(total) : 0.0) << ","
           << "\"rotation_row_major\":[";
    for (int row = 0; row < rotation.rows; ++row) {
        for (int col = 0; col < rotation.cols; ++col) {
            if (row != 0 || col != 0) {
                result << ",";
            }
            result << rotation.at<double>(row, col);
        }
    }
    result << "],\"translation_unit\":[";
    for (int row = 0; row < translation.rows; ++row) {
        if (row != 0) {
            result << ",";
        }
        result << translation.at<double>(row, 0);
    }
    result << "]}";
    return env->NewStringUTF(result.str().c_str());
#else
    return env->NewStringUTF(
        "{\"success\":false,"
        "\"status\":\"opencv_unavailable\","
        "\"detail\":\"OpenCV-backed metric pose solver is not linked in this build.\","
        "\"inlier_count\":0,"
        "\"inlier_ratio\":0.0,"
        "\"rotation_row_major\":[],"
        "\"translation_unit\":[]}"
    );
#endif
}
