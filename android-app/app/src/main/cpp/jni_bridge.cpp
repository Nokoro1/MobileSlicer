#include <jni.h>

#include <string>
#include <vector>

#if defined(__ANDROID__)
#include <dlfcn.h>
#endif

#include "orca_wrapper.h"

namespace {
void trim_native_heap()
{
#if defined(__ANDROID__)
    using MallocTrimFn = int (*)(size_t);
    static MallocTrimFn malloc_trim_fn = reinterpret_cast<MallocTrimFn>(
        dlsym(RTLD_DEFAULT, "malloc_trim"));
    if (malloc_trim_fn != nullptr) {
        malloc_trim_fn(0);
    }
#endif
}

class JniStringArrayUtf {
public:
    JniStringArrayUtf(JNIEnv* env, jobjectArray values, jsize count)
        : env_(env)
    {
        local_strings_.reserve(static_cast<size_t>(count));
        raw_strings_.reserve(static_cast<size_t>(count));

        for (jsize index = 0; index < count; ++index) {
            auto value = static_cast<jstring>(env_->GetObjectArrayElement(values, index));
            if (value == nullptr) {
                ok_ = false;
                return;
            }
            const char* raw_value = env_->GetStringUTFChars(value, nullptr);
            if (raw_value == nullptr) {
                env_->DeleteLocalRef(value);
                ok_ = false;
                return;
            }
            local_strings_.push_back(value);
            raw_strings_.push_back(raw_value);
        }
    }

    ~JniStringArrayUtf()
    {
        for (size_t index = 0; index < local_strings_.size(); ++index) {
            env_->ReleaseStringUTFChars(local_strings_[index], raw_strings_[index]);
            env_->DeleteLocalRef(local_strings_[index]);
        }
    }

    JniStringArrayUtf(const JniStringArrayUtf&) = delete;
    JniStringArrayUtf& operator=(const JniStringArrayUtf&) = delete;

    bool ok() const { return ok_; }
    const char* const* data() const { return raw_strings_.data(); }

private:
    JNIEnv* env_;
    std::vector<jstring> local_strings_;
    std::vector<const char*> raw_strings_;
    bool ok_ = true;
};

class JniUtfString {
public:
    JniUtfString(JNIEnv* env, jstring value)
        : env_(env), value_(value), raw_(env->GetStringUTFChars(value, nullptr))
    {
    }

    ~JniUtfString()
    {
        if (raw_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, raw_);
        }
    }

    JniUtfString(const JniUtfString&) = delete;
    JniUtfString& operator=(const JniUtfString&) = delete;

    bool ok() const { return raw_ != nullptr; }
    const char* get() const { return raw_; }

private:
    JNIEnv* env_;
    jstring value_;
    const char* raw_;
};

class JniDoubleArrayElements {
public:
    JniDoubleArrayElements(JNIEnv* env, jdoubleArray values)
        : env_(env), values_(values), raw_(env->GetDoubleArrayElements(values, nullptr))
    {
    }

    ~JniDoubleArrayElements()
    {
        if (raw_ != nullptr) {
            env_->ReleaseDoubleArrayElements(values_, raw_, JNI_ABORT);
        }
    }

    JniDoubleArrayElements(const JniDoubleArrayElements&) = delete;
    JniDoubleArrayElements& operator=(const JniDoubleArrayElements&) = delete;

    bool ok() const { return raw_ != nullptr; }
    const double* data() const { return reinterpret_cast<const double*>(raw_); }

private:
    JNIEnv* env_;
    jdoubleArray values_;
    jdouble* raw_;
};

class JniFloatArrayElements {
public:
    JniFloatArrayElements(JNIEnv* env, jfloatArray values)
        : env_(env), values_(values), raw_(env->GetFloatArrayElements(values, nullptr))
    {
    }

    ~JniFloatArrayElements()
    {
        if (raw_ != nullptr) {
            env_->ReleaseFloatArrayElements(values_, raw_, JNI_ABORT);
        }
    }

    JniFloatArrayElements(const JniFloatArrayElements&) = delete;
    JniFloatArrayElements& operator=(const JniFloatArrayElements&) = delete;

    bool ok() const { return raw_ != nullptr; }
    const float* data() const { return reinterpret_cast<const float*>(raw_); }

private:
    JNIEnv* env_;
    jfloatArray values_;
    jfloat* raw_;
};

class JniIntArrayElements {
public:
    JniIntArrayElements(JNIEnv* env, jintArray values)
        : env_(env), values_(values), raw_(env->GetIntArrayElements(values, nullptr))
    {
    }

    ~JniIntArrayElements()
    {
        if (raw_ != nullptr) {
            env_->ReleaseIntArrayElements(values_, raw_, JNI_ABORT);
        }
    }

    JniIntArrayElements(const JniIntArrayElements&) = delete;
    JniIntArrayElements& operator=(const JniIntArrayElements&) = delete;

    bool ok() const { return raw_ != nullptr; }

    std::vector<int> copy(jsize count) const
    {
        std::vector<int> output(static_cast<size_t>(count));
        for (jsize index = 0; index < count; ++index) {
            output[static_cast<size_t>(index)] = static_cast<int>(raw_[index]);
        }
        return output;
    }

private:
    JNIEnv* env_;
    jintArray values_;
    jint* raw_;
};

OrcaEngine* engine_from_handle(jlong handle)
{
    return handle == 0 ? nullptr : reinterpret_cast<OrcaEngine*>(handle);
}

OrcaGcodeViewer* viewer_from_handle(jlong handle)
{
    return handle == 0 ? nullptr : reinterpret_cast<OrcaGcodeViewer*>(handle);
}

jboolean jni_bool_from_result(int result)
{
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

jstring new_nonempty_string(JNIEnv* env, const char* value)
{
    if (value == nullptr || value[0] == '\0') {
        return nullptr;
    }
    return env->NewStringUTF(value);
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeCreateEngine(JNIEnv*, jclass)
{
    return reinterpret_cast<jlong>(orca_create());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeConfigureRuntimePaths(
    JNIEnv* env,
    jclass,
    jstring resources_dir,
    jstring temporary_dir)
{
    if (resources_dir == nullptr || temporary_dir == nullptr) {
        return;
    }

    JniUtfString raw_resources_dir(env, resources_dir);
    if (!raw_resources_dir.ok()) {
        return;
    }
    JniUtfString raw_temporary_dir(env, temporary_dir);
    if (!raw_temporary_dir.ok()) {
        return;
    }

    orca_set_runtime_paths(raw_resources_dir.get(), raw_temporary_dir.get());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeDestroyEngine(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return;
    }
    orca_destroy(engine_from_handle(handle));
    trim_native_heap();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeTrimMemory(JNIEnv*, jclass)
{
    trim_native_heap();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadModel(JNIEnv* env, jclass, jlong handle, jstring path)
{
    if (handle == 0 || path == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_path(env, path);
    if (!raw_path.ok()) {
        return JNI_FALSE;
    }

    return jni_bool_from_result(orca_load_model(engine_from_handle(handle), raw_path.get()));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadPlateModels(JNIEnv* env, jclass, jlong handle, jobjectArray paths, jdoubleArray transforms, jintArray extruder_ids)
{
    if (handle == 0 || paths == nullptr || transforms == nullptr || extruder_ids == nullptr) {
        return JNI_FALSE;
    }

    const jsize count = env->GetArrayLength(paths);
    const jsize transform_count = env->GetArrayLength(transforms);
    const jsize extruder_count = env->GetArrayLength(extruder_ids);
    if (count <= 0 || transform_count != count * 7 || extruder_count != count) {
        return JNI_FALSE;
    }

    JniStringArrayUtf raw_paths(env, paths, count);
    if (!raw_paths.ok()) {
        return JNI_FALSE;
    }
    JniDoubleArrayElements raw_transforms(env, transforms);
    if (!raw_transforms.ok()) {
        return JNI_FALSE;
    }
    JniIntArrayElements raw_extruder_ids(env, extruder_ids);
    if (!raw_extruder_ids.ok()) {
        return JNI_FALSE;
    }
    std::vector<int> extruder_ids_copy = raw_extruder_ids.copy(count);

    const int result = orca_load_plate_models(
        engine_from_handle(handle),
        raw_paths.data(),
        raw_transforms.data(),
        extruder_ids_copy.data(),
        static_cast<int>(count)
    );

    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePlanPlateArrangement(JNIEnv* env, jclass, jlong handle, jobjectArray paths, jdoubleArray transforms, jintArray extruder_ids, jstring config_json, jboolean allow_rotation)
{
    if (handle == 0 || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || config_json == nullptr) {
        return nullptr;
    }

    const jsize count = env->GetArrayLength(paths);
    const jsize transform_count = env->GetArrayLength(transforms);
    const jsize extruder_count = env->GetArrayLength(extruder_ids);
    if (count <= 0 || transform_count != count * 7 || extruder_count != count) {
        return nullptr;
    }

    JniStringArrayUtf raw_paths(env, paths, count);
    if (!raw_paths.ok()) {
        return nullptr;
    }
    JniUtfString raw_config_json(env, config_json);
    if (!raw_config_json.ok()) {
        return nullptr;
    }
    JniDoubleArrayElements raw_transforms(env, transforms);
    if (!raw_transforms.ok()) {
        return nullptr;
    }
    JniIntArrayElements raw_extruder_ids(env, extruder_ids);
    if (!raw_extruder_ids.ok()) {
        return nullptr;
    }

    std::vector<int> extruder_ids_copy = raw_extruder_ids.copy(count);
    std::vector<double> planned(static_cast<size_t>(transform_count), 0.0);
    const int result = orca_plan_plate_arrangement(
        engine_from_handle(handle),
        raw_paths.data(),
        raw_transforms.data(),
        extruder_ids_copy.data(),
        static_cast<int>(count),
        raw_config_json.get(),
        allow_rotation == JNI_TRUE ? 1 : 0,
        planned.data()
    );

    if (result != 0) {
        return nullptr;
    }

    jdoubleArray output = env->NewDoubleArray(transform_count);
    if (output == nullptr) {
        return nullptr;
    }
    env->SetDoubleArrayRegion(output, 0, transform_count, planned.data());
    return output;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePlanAutoOrientation(JNIEnv* env, jclass, jlong handle, jobjectArray paths, jdoubleArray transforms, jintArray extruder_ids, jstring config_json)
{
    if (handle == 0 || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || config_json == nullptr) {
        return nullptr;
    }

    const jsize count = env->GetArrayLength(paths);
    const jsize transform_count = env->GetArrayLength(transforms);
    const jsize extruder_count = env->GetArrayLength(extruder_ids);
    if (count <= 0 || transform_count != count * 7 || extruder_count != count) {
        return nullptr;
    }

    JniStringArrayUtf raw_paths(env, paths, count);
    if (!raw_paths.ok()) {
        return nullptr;
    }
    JniUtfString raw_config_json(env, config_json);
    if (!raw_config_json.ok()) {
        return nullptr;
    }
    JniDoubleArrayElements raw_transforms(env, transforms);
    if (!raw_transforms.ok()) {
        return nullptr;
    }
    JniIntArrayElements raw_extruder_ids(env, extruder_ids);
    if (!raw_extruder_ids.ok()) {
        return nullptr;
    }

    std::vector<int> extruder_ids_copy = raw_extruder_ids.copy(count);
    std::vector<double> planned(static_cast<size_t>(transform_count), 0.0);
    const int result = orca_plan_auto_orientation(
        engine_from_handle(handle),
        raw_paths.data(),
        raw_transforms.data(),
        extruder_ids_copy.data(),
        static_cast<int>(count),
        raw_config_json.get(),
        planned.data()
    );

    if (result != 0) {
        return nullptr;
    }

    jdoubleArray output = env->NewDoubleArray(transform_count);
    if (output == nullptr) {
        return nullptr;
    }
    env->SetDoubleArrayRegion(output, 0, transform_count, planned.data());
    return output;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetModelPlacement(JNIEnv*, jclass, jlong handle, jdouble x_mm, jdouble y_mm, jdouble z_mm)
{
    if (handle == 0) {
        return JNI_FALSE;
    }

    const int result = orca_set_model_placement(
        engine_from_handle(handle),
        static_cast<double>(x_mm),
        static_cast<double>(y_mm),
        static_cast<double>(z_mm)
    );
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetModelTransform(JNIEnv*, jclass, jlong handle, jdouble x_mm, jdouble y_mm, jdouble z_mm, jdouble rotation_x_radians, jdouble rotation_y_radians, jdouble rotation_z_radians, jdouble uniform_scale)
{
    if (handle == 0) {
        return JNI_FALSE;
    }

    const int result = orca_set_model_transform(
        engine_from_handle(handle),
        static_cast<double>(x_mm),
        static_cast<double>(y_mm),
        static_cast<double>(z_mm),
        static_cast<double>(rotation_x_radians),
        static_cast<double>(rotation_y_radians),
        static_cast<double>(rotation_z_radians),
        static_cast<double>(uniform_scale)
    );
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetConfigJson(JNIEnv* env, jclass, jlong handle, jstring json)
{
    if (handle == 0 || json == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_json(env, json);
    if (!raw_json.ok()) {
        return JNI_FALSE;
    }

    return jni_bool_from_result(orca_set_config_json(engine_from_handle(handle), raw_json.get()));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSlice(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }

    trim_native_heap();
    const int result = orca_slice(engine_from_handle(handle));
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetGcode(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    return new_nonempty_string(env, orca_get_gcode(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetGcodeSummary(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    return new_nonempty_string(env, orca_get_gcode_summary(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetEnrichedGcodeSummary(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    return new_nonempty_string(env, orca_get_enriched_gcode_summary(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetSliceMetrics(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    return new_nonempty_string(env, orca_get_slice_metrics(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePlanLatestSlicePreviewRanges(JNIEnv* env, jclass, jlong handle, jlong min_layer, jlong max_layer, jlong vertex_budget)
{
    if (handle == 0) {
        return nullptr;
    }

    const char* plan = orca_gcode_preview_suggest_layer_ranges(
        engine_from_handle(handle),
        static_cast<long>(min_layer),
        static_cast<long>(max_layer),
        static_cast<long>(vertex_budget)
    );
    return new_nonempty_string(env, plan);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeWriteGcodeToFile(JNIEnv* env, jclass, jlong handle, jstring path)
{
    if (handle == 0 || path == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_path(env, path);
    if (!raw_path.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_write_gcode_to_file(engine_from_handle(handle), raw_path.get());
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetLastError(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    return new_nonempty_string(env, orca_get_last_error(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeCreateGcodeViewer(JNIEnv*, jclass)
{
    return reinterpret_cast<jlong>(orca_gcode_viewer_create());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeDestroyGcodeViewer(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return;
    }
    orca_gcode_viewer_destroy(viewer_from_handle(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeShutdownGcodeViewer(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_TRUE;
    }
    return jni_bool_from_result(orca_gcode_viewer_shutdown(viewer_from_handle(handle)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadGcodeIntoGcodeViewer(JNIEnv* env, jclass, jlong viewer_handle, jstring gcode_text)
{
    if (viewer_handle == 0 || gcode_text == nullptr) {
        return JNI_FALSE;
    }
    JniUtfString gcode(env, gcode_text);
    if (!gcode.ok()) {
        return JNI_FALSE;
    }
    const int result = orca_gcode_viewer_load_gcode(
        viewer_from_handle(viewer_handle),
        gcode.get()
    );
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadLatestSliceIntoGcodeViewer(JNIEnv*, jclass, jlong viewer_handle, jlong engine_handle, jlong min_layer, jlong max_layer, jint lod_hint)
{
    if (viewer_handle == 0 || engine_handle == 0) {
        return JNI_FALSE;
    }
    const int result = orca_gcode_viewer_load_latest_slice(
        viewer_from_handle(viewer_handle),
        engine_from_handle(engine_handle),
        static_cast<long>(min_layer),
        static_cast<long>(max_layer),
        static_cast<int>(lod_hint)
    );
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeRenderGcodeViewer(JNIEnv* env, jclass, jlong viewer_handle, jfloatArray view_matrix, jfloatArray projection_matrix)
{
    if (viewer_handle == 0 || view_matrix == nullptr || projection_matrix == nullptr) {
        return JNI_FALSE;
    }
    if (env->GetArrayLength(view_matrix) != 16 || env->GetArrayLength(projection_matrix) != 16) {
        return JNI_FALSE;
    }
    JniFloatArrayElements view(env, view_matrix);
    if (!view.ok()) {
        return JNI_FALSE;
    }
    JniFloatArrayElements projection(env, projection_matrix);
    if (!projection.ok()) {
        return JNI_FALSE;
    }
    const int result = orca_gcode_viewer_render(
        viewer_from_handle(viewer_handle),
        view.data(),
        projection.data()
    );
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetGcodeViewerLayersCount(JNIEnv*, jclass, jlong viewer_handle)
{
    if (viewer_handle == 0) {
        return 0;
    }
    return static_cast<jlong>(orca_gcode_viewer_get_layers_count(viewer_from_handle(viewer_handle)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetGcodeViewerLayerRange(JNIEnv*, jclass, jlong viewer_handle, jlong min_layer, jlong max_layer)
{
    if (viewer_handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_gcode_viewer_set_layers_view_range(
        viewer_from_handle(viewer_handle),
        static_cast<long>(min_layer),
        static_cast<long>(max_layer)
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetGcodeViewerExtrusionWidthScale(JNIEnv*, jclass, jlong viewer_handle, jfloat scale)
{
    if (viewer_handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_gcode_viewer_set_extrusion_width_scale(
        viewer_from_handle(viewer_handle),
        static_cast<float>(scale)
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetGcodeViewerPathVisibility(JNIEnv*, jclass, jlong viewer_handle, jint kind, jint id, jboolean visible)
{
    if (viewer_handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_gcode_viewer_set_path_visibility(
        viewer_from_handle(viewer_handle),
        static_cast<int>(kind),
        static_cast<int>(id),
        visible == JNI_TRUE ? 1 : 0
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetGcodeViewerViewType(JNIEnv*, jclass, jlong viewer_handle, jint view_type)
{
    if (viewer_handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_gcode_viewer_set_view_type(
        viewer_from_handle(viewer_handle),
        static_cast<int>(view_type)
    ));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetGcodeViewerLastError(JNIEnv* env, jclass, jlong viewer_handle)
{
    if (viewer_handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_gcode_viewer_get_last_error(viewer_from_handle(viewer_handle)));
}
