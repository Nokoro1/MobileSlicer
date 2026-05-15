#include <jni.h>

#include <array>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#if defined(__ANDROID__)
#include <dlfcn.h>
#endif

#include "orca_wrapper.h"

namespace {
OrcaEngine* engine_from_handle(jlong handle);

std::mutex g_paint_overlay_buffer_mutex;
std::unordered_map<long long, std::vector<float>> g_paint_overlay_direct_buffers;
std::mutex g_engine_lifecycle_mutex;
std::unordered_set<OrcaEngine*> g_live_engines;

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

jobject paint_overlay_interleaved_direct_buffer(JNIEnv* env, jlong handle, bool delta)
{
    constexpr int kMaxPaintOverlayDeltaInterleavedFloats = 4200000;
    if (handle == 0) {
        return nullptr;
    }
    OrcaEngine* engine = engine_from_handle(handle);
    const int count = delta ?
        orca_paint_get_overlay_delta_interleaved(engine, nullptr, 0) :
        orca_paint_get_overlay_interleaved(engine, nullptr, 0);
    if (count <= 1) {
        return nullptr;
    }
    if (delta && count > kMaxPaintOverlayDeltaInterleavedFloats) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(g_paint_overlay_buffer_mutex);
    const long long key = (static_cast<long long>(handle) << 1) | (delta ? 1LL : 0LL);
    std::vector<float>& values = g_paint_overlay_direct_buffers[key];
    values.assign(static_cast<size_t>(count), 0.f);
    const int copied = delta ?
        orca_paint_get_overlay_delta_interleaved(engine, values.data(), count) :
        orca_paint_get_overlay_interleaved(engine, values.data(), count);
    if (copied != count) {
        values.clear();
        return nullptr;
    }
    return env->NewDirectByteBuffer(values.data(), static_cast<jlong>(values.size() * sizeof(float)));
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

class JniLongArrayElements {
public:
    JniLongArrayElements(JNIEnv* env, jlongArray values)
        : env_(env), values_(values), raw_(env->GetLongArrayElements(values, nullptr))
    {
    }

    ~JniLongArrayElements()
    {
        if (raw_ != nullptr) {
            env_->ReleaseLongArrayElements(values_, raw_, JNI_ABORT);
        }
    }

    JniLongArrayElements(const JniLongArrayElements&) = delete;
    JniLongArrayElements& operator=(const JniLongArrayElements&) = delete;

    bool ok() const { return raw_ != nullptr; }

    std::vector<long long> copy(jsize count) const
    {
        std::vector<long long> output(static_cast<size_t>(count));
        for (jsize index = 0; index < count; ++index) {
            output[static_cast<size_t>(index)] = static_cast<long long>(raw_[index]);
        }
        return output;
    }

private:
    JNIEnv* env_;
    jlongArray values_;
    jlong* raw_;
};

class JniByteArrayElements {
public:
    JniByteArrayElements(JNIEnv* env, jbyteArray values)
        : env_(env), values_(values), raw_(env->GetByteArrayElements(values, nullptr))
    {
    }

    ~JniByteArrayElements()
    {
        if (raw_ != nullptr) {
            env_->ReleaseByteArrayElements(values_, raw_, JNI_ABORT);
        }
    }

    JniByteArrayElements(const JniByteArrayElements&) = delete;
    JniByteArrayElements& operator=(const JniByteArrayElements&) = delete;

    bool ok() const { return raw_ != nullptr; }
    const unsigned char* data() const { return reinterpret_cast<const unsigned char*>(raw_); }

private:
    JNIEnv* env_;
    jbyteArray values_;
    jbyte* raw_;
};

OrcaEngine* engine_from_handle(jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    OrcaEngine* engine = reinterpret_cast<OrcaEngine*>(handle);
    std::lock_guard<std::mutex> lock(g_engine_lifecycle_mutex);
    return g_live_engines.find(engine) == g_live_engines.end() ? nullptr : engine;
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
    std::string sanitized;
    for (const unsigned char* cursor = reinterpret_cast<const unsigned char*>(value); *cursor != '\0'; ++cursor) {
        const unsigned char byte = *cursor;
        if (byte == '\n' || byte == '\r' || byte == '\t' || (byte >= 0x20 && byte <= 0x7e)) {
            sanitized.push_back(static_cast<char>(byte));
        } else {
            sanitized.push_back('?');
        }
    }
    return sanitized.empty() ? nullptr : env->NewStringUTF(sanitized.c_str());
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeCreateEngine(JNIEnv*, jclass)
{
    OrcaEngine* engine = orca_create();
    if (engine == nullptr) {
        return 0;
    }
    {
        std::lock_guard<std::mutex> lock(g_engine_lifecycle_mutex);
        g_live_engines.insert(engine);
    }
    return reinterpret_cast<jlong>(engine);
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
    OrcaEngine* engine = reinterpret_cast<OrcaEngine*>(handle);
    {
        std::lock_guard<std::mutex> lifecycle_lock(g_engine_lifecycle_mutex);
        if (g_live_engines.erase(engine) == 0) {
            return;
        }
    }
    {
        std::lock_guard<std::mutex> lock(g_paint_overlay_buffer_mutex);
        g_paint_overlay_direct_buffers.erase(static_cast<long long>(handle) << 1);
        g_paint_overlay_direct_buffers.erase((static_cast<long long>(handle) << 1) | 1LL);
    }
    orca_destroy(engine);
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

extern "C" JNIEXPORT void JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeClearGeneratedGcode(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return;
    }

    orca_clear_generated_gcode(engine_from_handle(handle));
    trim_native_heap();
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadPlateModelsV2(JNIEnv* env, jclass, jlong handle, jobjectArray paths, jdoubleArray transforms, jintArray extruder_ids, jlongArray mobile_object_ids, jstring paint_payload_json)
{
    if (handle == 0 || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || mobile_object_ids == nullptr || paint_payload_json == nullptr) {
        return JNI_FALSE;
    }

    const jsize count = env->GetArrayLength(paths);
    const jsize transform_count = env->GetArrayLength(transforms);
    const jsize extruder_count = env->GetArrayLength(extruder_ids);
    const jsize object_id_count = env->GetArrayLength(mobile_object_ids);
    const jsize transform_stride = transform_count == count * 16 ? 16 : 7;
    if (count <= 0 || (transform_count != count * 7 && transform_count != count * 16) || extruder_count != count || object_id_count != count) {
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
    JniLongArrayElements raw_mobile_object_ids(env, mobile_object_ids);
    if (!raw_mobile_object_ids.ok()) {
        return JNI_FALSE;
    }
    JniUtfString raw_paint_payload(env, paint_payload_json);
    if (!raw_paint_payload.ok()) {
        return JNI_FALSE;
    }

    std::vector<int> extruder_ids_copy = raw_extruder_ids.copy(count);
    std::vector<long long> mobile_object_ids_copy = raw_mobile_object_ids.copy(count);

    const int result = orca_load_plate_models_v2(
        engine_from_handle(handle),
        raw_paths.data(),
        raw_transforms.data(),
        static_cast<int>(transform_stride),
        extruder_ids_copy.data(),
        mobile_object_ids_copy.data(),
        raw_paint_payload.get(),
        static_cast<int>(count)
    );

    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadProject3mf(JNIEnv* env, jclass, jlong handle, jstring path, jlongArray mobile_object_ids)
{
    if (handle == 0 || path == nullptr || mobile_object_ids == nullptr) {
        return JNI_FALSE;
    }

    const jsize object_id_count = env->GetArrayLength(mobile_object_ids);
    if (object_id_count <= 0) {
        return JNI_FALSE;
    }

    JniUtfString raw_path(env, path);
    if (!raw_path.ok()) {
        return JNI_FALSE;
    }
    JniLongArrayElements raw_mobile_object_ids(env, mobile_object_ids);
    if (!raw_mobile_object_ids.ok()) {
        return JNI_FALSE;
    }

    std::vector<long long> mobile_object_ids_copy = raw_mobile_object_ids.copy(object_id_count);
    const int result = orca_load_project_3mf(
        engine_from_handle(handle),
        raw_path.get(),
        mobile_object_ids_copy.data(),
        static_cast<int>(object_id_count)
    );
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeExtractModelMeshToStl(JNIEnv* env, jclass, jlong handle, jstring input_path, jstring output_stl_path)
{
    if (handle == 0 || input_path == nullptr || output_stl_path == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_input_path(env, input_path);
    JniUtfString raw_output_path(env, output_stl_path);
    if (!raw_input_path.ok() || !raw_output_path.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_extract_model_mesh_to_stl(
        engine_from_handle(handle),
        raw_input_path.get(),
        raw_output_path.get()
    );
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeConvertStepToStl(JNIEnv* env, jclass, jlong handle, jstring input_path, jstring output_stl_path, jdouble linear_deflection, jdouble angle_deflection)
{
    if (handle == 0 || input_path == nullptr || output_stl_path == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_input_path(env, input_path);
    JniUtfString raw_output_path(env, output_stl_path);
    if (!raw_input_path.ok() || !raw_output_path.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_convert_step_to_stl(
        engine_from_handle(handle),
        raw_input_path.get(),
        raw_output_path.get(),
        static_cast<double>(linear_deflection),
        static_cast<double>(angle_deflection)
    );
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSplitModelMeshToStls(JNIEnv* env, jclass, jlong handle, jstring input_path, jstring output_directory, jint split_mode)
{
    if (handle == 0 || input_path == nullptr || output_directory == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_input_path(env, input_path);
    JniUtfString raw_output_directory(env, output_directory);
    if (!raw_input_path.ok() || !raw_output_directory.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_split_model_mesh_to_stls(
        engine_from_handle(handle),
        raw_input_path.get(),
        raw_output_directory.get(),
        static_cast<int>(split_mode)
    );
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetLastSplitResultJson(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_get_last_split_result_json(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintBeginSession(JNIEnv*, jclass, jlong handle, jlong mobile_object_id, jint mode)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_begin_session(
        engine_from_handle(handle),
        static_cast<long long>(mobile_object_id),
        static_cast<int>(mode)
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeCutObject(JNIEnv* env, jclass, jlong handle, jstring request_json)
{
    if (handle == 0 || request_json == nullptr) {
        return JNI_FALSE;
    }
    JniUtfString raw_request(env, request_json);
    if (!raw_request.ok()) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_cut_object(engine_from_handle(handle), raw_request.get()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetLastCutResultJson(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_get_last_cut_result_json(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintBindingDebugJson(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    const char* debug_json = orca_paint_binding_debug_json(engine_from_handle(handle));
    return debug_json == nullptr ? nullptr : env->NewStringUTF(debug_json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintObjectBoundsJson(JNIEnv* env, jclass, jlong handle, jlong mobile_object_id)
{
    if (handle == 0 || mobile_object_id <= 0) {
        return nullptr;
    }
    const char* bounds_json = orca_paint_object_bounds_json(
        engine_from_handle(handle),
        static_cast<long long>(mobile_object_id));
    return bounds_json == nullptr ? nullptr : env->NewStringUTF(bounds_json);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintEndSession(JNIEnv*, jclass, jlong handle, jboolean commit)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_end_session(
        engine_from_handle(handle),
        commit == JNI_TRUE ? 1 : 0
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintHitTest(JNIEnv* env, jclass, jlong, jfloat, jfloat)
{
    if (jclass exception_class = env->FindClass("java/lang/UnsupportedOperationException")) {
        env->ThrowNew(exception_class, "screen-coordinate paint hit testing is disabled; use nativePaintHitTestRay");
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintHitTestRay(JNIEnv* env, jclass, jlong handle, jfloatArray ray)
{
    if (handle == 0 || ray == nullptr || env->GetArrayLength(ray) < 6) {
        return nullptr;
    }
    JniFloatArrayElements raw_ray(env, ray);
    if (!raw_ray.ok()) {
        return nullptr;
    }

    std::array<float, 9> hit{};
    const int result = orca_paint_hit_test_ray(engine_from_handle(handle), raw_ray.data(), hit.data());
    if (result != 0) {
        return nullptr;
    }

    jfloatArray output = env->NewFloatArray(static_cast<jsize>(hit.size()));
    if (output == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(output, 0, static_cast<jsize>(hit.size()), hit.data());
    return output;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintSetTool(
    JNIEnv*,
    jclass,
    jlong handle,
    jint brush_shape,
    jint action,
    jfloat brush_radius_mm,
    jfloat brush_height_mm,
    jint color_slot)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_set_tool(
        engine_from_handle(handle),
        static_cast<int>(brush_shape),
        static_cast<int>(action),
        static_cast<float>(brush_radius_mm),
        static_cast<float>(brush_height_mm),
        static_cast<int>(color_slot)
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintSetToolOptions(
    JNIEnv* env,
    jclass,
    jlong handle,
    jfloat smart_fill_angle_deg,
    jfloat overhang_angle_deg,
    jfloatArray clipping_plane)
{
    if (handle == 0) {
        return JNI_FALSE;
    }

    std::array<float, 4> clipping{};
    const float* clipping_ptr = nullptr;
    if (clipping_plane != nullptr) {
        if (env->GetArrayLength(clipping_plane) < 4) {
            return JNI_FALSE;
        }
        JniFloatArrayElements raw_clipping(env, clipping_plane);
        if (!raw_clipping.ok()) {
            return JNI_FALSE;
        }
        for (size_t i = 0; i < clipping.size(); ++i) {
            clipping[i] = raw_clipping.data()[i];
        }
        clipping_ptr = clipping.data();
    }

    return jni_bool_from_result(orca_paint_set_tool_options(
        engine_from_handle(handle),
        static_cast<float>(smart_fill_angle_deg),
        static_cast<float>(overhang_angle_deg),
        clipping_ptr
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintStrokeBegin(JNIEnv* env, jclass, jlong, jfloat, jfloat)
{
    if (jclass exception_class = env->FindClass("java/lang/UnsupportedOperationException")) {
        env->ThrowNew(exception_class, "screen-coordinate paint strokes are disabled; use nativePaintStrokeBeginRay");
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintStrokeBeginRay(JNIEnv* env, jclass, jlong handle, jfloatArray ray, jint brush_shape, jint action, jfloat brush_radius_mm)
{
    if (handle == 0 || ray == nullptr || env->GetArrayLength(ray) < 6) {
        return JNI_FALSE;
    }
    JniFloatArrayElements raw_ray(env, ray);
    if (!raw_ray.ok()) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_stroke_begin_ray(
        engine_from_handle(handle),
        raw_ray.data(),
        static_cast<int>(brush_shape),
        static_cast<int>(action),
        static_cast<float>(brush_radius_mm)
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintStrokeMove(JNIEnv* env, jclass, jlong, jfloat, jfloat)
{
    if (jclass exception_class = env->FindClass("java/lang/UnsupportedOperationException")) {
        env->ThrowNew(exception_class, "screen-coordinate paint strokes are disabled; use nativePaintStrokeMoveRay");
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintStrokeMoveRay(JNIEnv* env, jclass, jlong handle, jfloatArray ray, jint brush_shape, jint action, jfloat brush_radius_mm)
{
    if (handle == 0 || ray == nullptr || env->GetArrayLength(ray) < 6) {
        return JNI_FALSE;
    }
    JniFloatArrayElements raw_ray(env, ray);
    if (!raw_ray.ok()) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_stroke_move_ray(
        engine_from_handle(handle),
        raw_ray.data(),
        static_cast<int>(brush_shape),
        static_cast<int>(action),
        static_cast<float>(brush_radius_mm)
    ));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintStrokeEnd(JNIEnv*, jclass, jlong handle, jboolean commit)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(commit == JNI_TRUE ?
        orca_paint_stroke_end(engine_from_handle(handle)) :
        orca_paint_end_session(engine_from_handle(handle), 0));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintUndo(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_undo(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintRedo(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_redo(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintClear(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return jni_bool_from_result(orca_paint_clear(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintCanUndo(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return orca_paint_can_undo(engine_from_handle(handle)) != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintCanRedo(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return orca_paint_can_redo(engine_from_handle(handle)) != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintSerialize(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_paint_serialize(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintGetOverlay(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_paint_get_overlay(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintGetOverlayDelta(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_paint_get_overlay_delta(engine_from_handle(handle)));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintGetOverlayInterleaved(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }
    OrcaEngine* engine = engine_from_handle(handle);
    const int count = orca_paint_get_overlay_interleaved(engine, nullptr, 0);
    if (count <= 1) {
        return nullptr;
    }
    jfloatArray result = env->NewFloatArray(count);
    if (result == nullptr) {
        return nullptr;
    }
    std::vector<float> values(static_cast<size_t>(count), 0.f);
    const int copied = orca_paint_get_overlay_interleaved(engine, values.data(), count);
    if (copied != count) {
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, count, values.data());
    return result;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintGetOverlayInterleavedBuffer(JNIEnv* env, jclass, jlong handle)
{
    return paint_overlay_interleaved_direct_buffer(env, handle, false);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintGetOverlayDeltaInterleaved(JNIEnv* env, jclass, jlong handle)
{
    constexpr int kMaxPaintOverlayDeltaInterleavedFloats = 4200000;
    if (handle == 0) {
        return nullptr;
    }
    OrcaEngine* engine = engine_from_handle(handle);
    const int count = orca_paint_get_overlay_delta_interleaved(engine, nullptr, 0);
    if (count <= 1) {
        return nullptr;
    }
    if (count > kMaxPaintOverlayDeltaInterleavedFloats) {
        return nullptr;
    }
    jfloatArray result = env->NewFloatArray(count);
    if (result == nullptr) {
        return nullptr;
    }
    std::vector<float> values(static_cast<size_t>(count), 0.f);
    const int copied = orca_paint_get_overlay_delta_interleaved(engine, values.data(), count);
    if (copied != count) {
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, count, values.data());
    return result;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintGetOverlayDeltaInterleavedBuffer(JNIEnv* env, jclass, jlong handle)
{
    return paint_overlay_interleaved_direct_buffer(env, handle, true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePaintRemapColorSlots(JNIEnv* env, jclass, jlong handle, jstring payload_json, jintArray old_slot_to_new_slot)
{
    if (handle == 0 || payload_json == nullptr || old_slot_to_new_slot == nullptr) {
        return nullptr;
    }
    const jsize slot_count = env->GetArrayLength(old_slot_to_new_slot);
    if (slot_count <= 0) {
        return nullptr;
    }
    JniUtfString payload(env, payload_json);
    JniIntArrayElements remap(env, old_slot_to_new_slot);
    if (!payload.ok() || !remap.ok()) {
        return nullptr;
    }
    const std::vector<int> copied_remap = remap.copy(slot_count);
    return new_nonempty_string(
        env,
        orca_paint_remap_color_slots(
            engine_from_handle(handle),
            payload.get(),
            copied_remap.data(),
            static_cast<int>(copied_remap.size())));
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
    const jsize transform_stride = transform_count == count * 16 ? 16 : 7;
    if (count <= 0 || (transform_count != count * 7 && transform_count != count * 16) || extruder_count != count) {
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

    constexpr jsize output_transform_stride = 17;
    const jsize output_transform_count = count * output_transform_stride;
    std::vector<int> extruder_ids_copy = raw_extruder_ids.copy(count);
    std::vector<double> planned(static_cast<size_t>(output_transform_count), 0.0);
    std::vector<double> native_transforms(static_cast<size_t>(count) * 16, 0.0);
    std::vector<int> planned_beds(static_cast<size_t>(count), 0);
    const int result = orca_plan_plate_arrangement(
        engine_from_handle(handle),
        raw_paths.data(),
        raw_transforms.data(),
        static_cast<int>(transform_stride),
        extruder_ids_copy.data(),
        static_cast<int>(count),
        raw_config_json.get(),
        allow_rotation == JNI_TRUE ? 1 : 0,
        native_transforms.data(),
        planned_beds.data()
    );

    if (result != 0) {
        return nullptr;
    }

    for (jsize index = 0; index < count; ++index) {
        const jsize source_offset = index * 16;
        const jsize target_offset = index * output_transform_stride;
        for (jsize value_index = 0; value_index < 16; ++value_index) {
            planned[static_cast<size_t>(target_offset + value_index)] =
                native_transforms[static_cast<size_t>(source_offset + value_index)];
        }
        planned[static_cast<size_t>(target_offset + 16)] = static_cast<double>(planned_beds[static_cast<size_t>(index)]);
    }

    jdoubleArray output = env->NewDoubleArray(output_transform_count);
    if (output == nullptr) {
        return nullptr;
    }
    env->SetDoubleArrayRegion(output, 0, output_transform_count, planned.data());
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
    const jsize transform_stride = transform_count == count * 16 ? 16 : 7;
    if (count <= 0 || (transform_count != count * 7 && transform_count != count * 16) || extruder_count != count) {
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

    constexpr jsize output_transform_stride = 16;
    const jsize output_transform_count = count * output_transform_stride;
    std::vector<int> extruder_ids_copy = raw_extruder_ids.copy(count);
    std::vector<double> planned(static_cast<size_t>(output_transform_count), 0.0);
    const int result = orca_plan_auto_orientation(
        engine_from_handle(handle),
        raw_paths.data(),
        raw_transforms.data(),
        static_cast<int>(transform_stride),
        extruder_ids_copy.data(),
        static_cast<int>(count),
        raw_config_json.get(),
        planned.data()
    );

    if (result != 0) {
        return nullptr;
    }

    jdoubleArray output = env->NewDoubleArray(output_transform_count);
    if (output == nullptr) {
        return nullptr;
    }
    env->SetDoubleArrayRegion(output, 0, output_transform_count, planned.data());
    return output;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativePrewarmPlatePlanningModels(JNIEnv* env, jclass, jlong handle, jobjectArray paths)
{
    if (handle == 0 || paths == nullptr) {
        return JNI_FALSE;
    }
    const jsize count = env->GetArrayLength(paths);
    if (count <= 0) {
        return JNI_FALSE;
    }
    JniStringArrayUtf raw_paths(env, paths, count);
    if (!raw_paths.ok()) {
        return JNI_FALSE;
    }
    return orca_prewarm_plate_planning_models(
        engine_from_handle(handle),
        raw_paths.data(),
        static_cast<int>(count)
    ) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeCancelPlanning(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_FALSE;
    }
    return orca_cancel_planning(engine_from_handle(handle)) == 0 ? JNI_TRUE : JNI_FALSE;
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
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetThumbnailRequestsJson(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    std::lock_guard<std::mutex> lifecycle_lock(g_engine_lifecycle_mutex);
    OrcaEngine* engine = reinterpret_cast<OrcaEngine*>(handle);
    if (g_live_engines.find(engine) == g_live_engines.end()) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_get_thumbnail_requests_json(engine));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetSlicedPlateBboxJson(JNIEnv* env, jclass, jlong handle)
{
    if (handle == 0) {
        return nullptr;
    }

    std::lock_guard<std::mutex> lifecycle_lock(g_engine_lifecycle_mutex);
    OrcaEngine* engine = reinterpret_cast<OrcaEngine*>(handle);
    if (g_live_engines.find(engine) == g_live_engines.end()) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_get_sliced_plate_bbox_json(engine));
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeClearSliceThumbnails(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return;
    }

    std::lock_guard<std::mutex> lifecycle_lock(g_engine_lifecycle_mutex);
    OrcaEngine* engine = reinterpret_cast<OrcaEngine*>(handle);
    if (g_live_engines.find(engine) == g_live_engines.end()) {
        return;
    }
    orca_clear_slice_thumbnails(engine);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeAddSliceThumbnailRgba(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint width,
    jint height,
    jstring format,
    jstring role,
    jbyteArray rgba)
{
    if (handle == 0 || width <= 0 || height <= 0 || format == nullptr || role == nullptr || rgba == nullptr) {
        return JNI_FALSE;
    }
    const jsize byte_count = env->GetArrayLength(rgba);
    JniUtfString raw_format(env, format);
    JniUtfString raw_role(env, role);
    JniByteArrayElements raw_rgba(env, rgba);
    if (!raw_format.ok() || !raw_role.ok() || !raw_rgba.ok()) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lifecycle_lock(g_engine_lifecycle_mutex);
    OrcaEngine* engine = reinterpret_cast<OrcaEngine*>(handle);
    if (g_live_engines.find(engine) == g_live_engines.end()) {
        return JNI_FALSE;
    }
    const int result = orca_add_slice_thumbnail_rgba(
        engine,
        static_cast<int>(width),
        static_cast<int>(height),
        raw_format.get(),
        raw_role.get(),
        raw_rgba.data(),
        static_cast<int>(byte_count));
    return jni_bool_from_result(result);
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeWriteBambuGcode3mfToFile(JNIEnv* env, jclass, jlong handle, jstring path)
{
    if (handle == 0 || path == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_path(env, path);
    if (!raw_path.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_write_bambu_gcode_3mf_to_file(engine_from_handle(handle), raw_path.get());
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeWriteMultiPlateBambuGcode3mfToFile(JNIEnv* env, jclass, jlong handle, jstring path, jstring manifestJson)
{
    if (handle == 0 || path == nullptr || manifestJson == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_path(env, path);
    JniUtfString raw_manifest(env, manifestJson);
    if (!raw_path.ok() || !raw_manifest.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_write_multi_plate_bambu_gcode_3mf_to_file(
        engine_from_handle(handle),
        raw_path.get(),
        raw_manifest.get());
    trim_native_heap();
    return jni_bool_from_result(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeWriteProject3mfToFile(JNIEnv* env, jclass, jlong handle, jstring path)
{
    if (handle == 0 || path == nullptr) {
        return JNI_FALSE;
    }

    JniUtfString raw_path(env, path);
    if (!raw_path.ok()) {
        return JNI_FALSE;
    }

    const int result = orca_write_project_3mf_to_file(engine_from_handle(handle), raw_path.get());
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
    trim_native_heap();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeShutdownGcodeViewer(JNIEnv*, jclass, jlong handle)
{
    if (handle == 0) {
        return JNI_TRUE;
    }
    const int result = orca_gcode_viewer_shutdown(viewer_from_handle(handle));
    trim_native_heap();
    return jni_bool_from_result(result);
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
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeSetGcodePreviewGeneration(JNIEnv*, jclass, jlong engine_handle, jlong generation)
{
    if (engine_handle == 0) {
        return JNI_FALSE;
    }
    orca_set_gcode_preview_generation(engine_from_handle(engine_handle), static_cast<long>(generation));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeLoadLatestSliceIntoGcodeViewer(JNIEnv*, jclass, jlong viewer_handle, jlong engine_handle, jlong min_layer, jlong max_layer, jint lod_hint, jlong generation)
{
    if (viewer_handle == 0 || engine_handle == 0) {
        return JNI_FALSE;
    }
    const int result = orca_gcode_viewer_load_latest_slice(
        viewer_from_handle(viewer_handle),
        engine_from_handle(engine_handle),
        static_cast<long>(min_layer),
        static_cast<long>(max_layer),
        static_cast<int>(lod_hint),
        static_cast<long>(generation)
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

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileslicer_nativebridge_NativeEngineBridge_nativeGetGcodeViewerLastLoadMetrics(JNIEnv* env, jclass, jlong viewer_handle)
{
    if (viewer_handle == 0) {
        return nullptr;
    }
    return new_nonempty_string(env, orca_gcode_viewer_get_last_load_metrics(viewer_from_handle(viewer_handle)));
}
