#include <jni.h>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

extern "C" {
#include "apriltag.h"
#include "tag36h11.h"
#include "common/image_u8.h"
}

namespace {

std::string json_escape(const char* text)
{
    std::string escaped;
    if (!text) {
        return escaped;
    }
    for (const char* c = text; *c != '\0'; ++c) {
        switch (*c) {
        case '\\':
            escaped += "\\\\";
            break;
        case '"':
            escaped += "\\\"";
            break;
        case '\n':
            escaped += "\\n";
            break;
        case '\r':
            escaped += "\\r";
            break;
        case '\t':
            escaped += "\\t";
            break;
        default:
            escaped += *c;
            break;
        }
    }
    return escaped;
}

std::string detection_json(
    apriltag_detection_t* detection,
    const std::string& frame_id,
    const std::string& frame_path)
{
    std::string result = "{\"id\":\"";
    result += std::to_string(detection->id);
    result += "\",\"frame_id\":\"";
    result += frame_id;
    result += "\",\"frame_path\":\"";
    result += frame_path;
    result += "\",\"marker_size_mm\":32.0,\"hamming\":";
    result += std::to_string(detection->hamming);
    result += ",\"decision_margin\":";
    result += std::to_string(detection->decision_margin);
    result += ",\"corners_px\":[";
    for (int i = 0; i < 4; ++i) {
        if (i > 0) {
            result += ",";
        }
        result += "[";
        result += std::to_string(detection->p[i][0]);
        result += ",";
        result += std::to_string(detection->p[i][1]);
        result += "]";
    }
    result += "]}";
    return result;
}

}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_scanner_ScannerAprilTagNativeBridge_nativeStatusJson(JNIEnv* env, jobject)
{
    return env->NewStringUTF(
        "{\"available\":true,"
        "\"status\":\"ready\","
        "\"detail\":\"AprilRobotics AprilTag tag36h11 detector linked.\"}"
    );
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_scanner_ScannerAprilTagNativeBridge_nativeDetectTag36h11Json(
    JNIEnv* env,
    jobject,
    jbyteArray grayscale,
    jint width,
    jint height,
    jstring frame_id,
    jstring frame_path)
{
    const char* frame_id_chars = env->GetStringUTFChars(frame_id, nullptr);
    const char* frame_path_chars = env->GetStringUTFChars(frame_path, nullptr);
    const std::string escaped_frame_id = json_escape(frame_id_chars);
    const std::string escaped_frame_path = json_escape(frame_path_chars);

    std::string result = "{\"detector\":\"apriltag_36h11_jni\",";
    result += "\"status\":\"ready\",";
    result += "\"frame_id\":\"";
    result += escaped_frame_id;
    result += "\",\"frame_path\":\"";
    result += escaped_frame_path;
    result += "\",\"detections\":[";

    const jsize grayscale_length = env->GetArrayLength(grayscale);
    if (width > 0 && height > 0 && grayscale_length >= width * height) {
        std::vector<uint8_t> pixels(static_cast<size_t>(width) * static_cast<size_t>(height));
        env->GetByteArrayRegion(
            grayscale,
            0,
            static_cast<jsize>(pixels.size()),
            reinterpret_cast<jbyte*>(pixels.data())
        );

        image_u8_t* image = image_u8_create(static_cast<unsigned int>(width), static_cast<unsigned int>(height));
        if (!image) {
            result += "{\"error\":\"image_allocation_failed\"}";
            result += "]}";
            if (frame_id_chars) {
                env->ReleaseStringUTFChars(frame_id, frame_id_chars);
            }
            if (frame_path_chars) {
                env->ReleaseStringUTFChars(frame_path, frame_path_chars);
            }
            return env->NewStringUTF(result.c_str());
        }
        for (int y = 0; y < height; ++y) {
            memcpy(
                &image->buf[y * image->stride],
                &pixels[static_cast<size_t>(y) * static_cast<size_t>(width)],
                static_cast<size_t>(width)
            );
        }

        apriltag_family_t* family = tag36h11_create();
        apriltag_detector_t* detector = apriltag_detector_create();
        apriltag_detector_add_family_bits(detector, family, 1);
        detector->quad_decimate = 1.0;
        detector->quad_sigma = 0.0;
        detector->nthreads = 1;
        detector->debug = 0;
        detector->refine_edges = 1;

        zarray_t* detections = apriltag_detector_detect(detector, image);
        for (int i = 0; i < zarray_size(detections); ++i) {
            apriltag_detection_t* detection = nullptr;
            zarray_get(detections, i, &detection);
            if (i > 0) {
                result += ",";
            }
            result += detection_json(detection, escaped_frame_id, escaped_frame_path);
        }
        apriltag_detections_destroy(detections);
        apriltag_detector_destroy(detector);
        tag36h11_destroy(family);
        image_u8_destroy(image);
    } else {
        result += "{\"error\":\"invalid_grayscale_input\"}";
    }

    result += "]}";
    if (frame_id_chars) {
        env->ReleaseStringUTFChars(frame_id, frame_id_chars);
    }
    if (frame_path_chars) {
        env->ReleaseStringUTFChars(frame_path, frame_path_chars);
    }
    return env->NewStringUTF(result.c_str());
}
