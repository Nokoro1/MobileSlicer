#ifndef ORCA_WRAPPER_H
#define ORCA_WRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct OrcaEngine OrcaEngine;
typedef struct OrcaGcodeViewer OrcaGcodeViewer;

void orca_set_runtime_paths(const char* resources_dir, const char* temporary_dir);
OrcaEngine* orca_create(void);
void orca_destroy(OrcaEngine* engine);

int orca_load_model(OrcaEngine* engine, const char* path);
int orca_load_plate_models(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count);
int orca_load_plate_models_v2(OrcaEngine* engine, const char* const* paths, const double* transforms, int transform_stride, const int* extruder_ids, const long long* mobile_object_ids, const char* paint_payload_json, int count);
int orca_load_plate_models_v3(OrcaEngine* engine, const char* const* paths, const char* const* source_paths, const double* transforms, int transform_stride, const int* extruder_ids, const long long* mobile_object_ids, const char* paint_payload_json, int count);
int orca_load_project_3mf(OrcaEngine* engine, const char* path, const long long* mobile_object_ids, int count);
int orca_prewarm_plate_planning_models(OrcaEngine* engine, const char* const* paths, int count);
int orca_extract_model_mesh_to_stl(OrcaEngine* engine, const char* input_path, const char* output_stl_path);
int orca_convert_step_to_stl(OrcaEngine* engine, const char* input_path, const char* output_stl_path, double linear_deflection, double angle_deflection);
int orca_split_model_mesh_to_stls(OrcaEngine* engine, const char* input_path, const char* output_directory, int split_mode);
const char* orca_get_last_split_result_json(OrcaEngine* engine);
int orca_cut_object(OrcaEngine* engine, const char* request_json);
const char* orca_get_last_cut_result_json(OrcaEngine* engine);
int orca_paint_begin_session(OrcaEngine* engine, long long mobile_object_id, int mode);
const char* orca_paint_binding_debug_json(OrcaEngine* engine);
const char* orca_paint_object_bounds_json(OrcaEngine* engine, long long mobile_object_id);
int orca_paint_end_session(OrcaEngine* engine, int commit);
int orca_paint_set_tool(OrcaEngine* engine, int shape, int action, float radius_mm, float height_mm, int color_slot);
int orca_paint_set_tool_options(OrcaEngine* engine, float smart_fill_angle_deg, float overhang_angle_deg, const float* clipping_plane);
int orca_paint_hit_test(OrcaEngine* engine, const float* ray, float* out_hit);
int orca_paint_hit_test_ray(OrcaEngine* engine, const float* ray, float* out_hit);
int orca_paint_stroke_begin(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm);
int orca_paint_stroke_begin_ray(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm);
int orca_paint_stroke_move(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm);
int orca_paint_stroke_move_ray(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm);
int orca_paint_stroke_end(OrcaEngine* engine);
int orca_paint_undo(OrcaEngine* engine);
int orca_paint_redo(OrcaEngine* engine);
int orca_paint_clear(OrcaEngine* engine);
int orca_paint_can_undo(OrcaEngine* engine);
int orca_paint_can_redo(OrcaEngine* engine);
const char* orca_paint_serialize(OrcaEngine* engine);
const char* orca_paint_get_overlay(OrcaEngine* engine);
int orca_paint_get_overlay_interleaved(OrcaEngine* engine, float* out_values, int max_values);
const char* orca_paint_get_overlay_delta(OrcaEngine* engine);
int orca_paint_get_overlay_delta_interleaved(OrcaEngine* engine, float* out_values, int max_values);
const char* orca_paint_remap_color_slots(OrcaEngine* engine, const char* payload_json, const int* old_slot_to_new_slot, int slot_count);
int orca_plan_plate_arrangement(OrcaEngine* engine, const char* const* paths, const double* transforms, int transform_stride, const int* extruder_ids, int count, const char* config_json, int allow_rotation, double* out_transforms, int* out_bed_indices);
int orca_plan_auto_orientation(OrcaEngine* engine, const char* const* paths, const double* transforms, int transform_stride, const int* extruder_ids, int count, const char* config_json, double* out_transforms);
int orca_cancel_planning(OrcaEngine* engine);
int orca_set_model_placement(OrcaEngine* engine, double x_mm, double y_mm, double z_mm);
int orca_set_model_transform(OrcaEngine* engine, double x_mm, double y_mm, double z_mm, double rotation_x_radians, double rotation_y_radians, double rotation_z_radians, double uniform_scale);
int orca_set_config_json(OrcaEngine* engine, const char* json);
int orca_slice(OrcaEngine* engine);
void orca_clear_generated_gcode(OrcaEngine* engine);
void orca_clear_slice_thumbnails(OrcaEngine* engine);
int orca_add_slice_thumbnail_rgba(OrcaEngine* engine, int width, int height, const char* format, const char* role, const unsigned char* rgba, int byte_count);

const char* orca_get_gcode(OrcaEngine* engine);
const char* orca_get_gcode_summary(OrcaEngine* engine);
const char* orca_get_enriched_gcode_summary(OrcaEngine* engine);
const char* orca_get_slice_metrics(OrcaEngine* engine);
const char* orca_get_sliced_plate_bbox_json(OrcaEngine* engine);
const char* orca_get_thumbnail_requests_json(OrcaEngine* engine);
int orca_write_gcode_to_file(OrcaEngine* engine, const char* path);
int orca_write_project_3mf_to_file(OrcaEngine* engine, const char* path);
int orca_write_bambu_gcode_3mf_to_file(OrcaEngine* engine, const char* path);
int orca_write_multi_plate_bambu_gcode_3mf_to_file(OrcaEngine* engine, const char* path, const char* manifest_json);
const char* orca_get_last_error(OrcaEngine* engine);

OrcaGcodeViewer* orca_gcode_viewer_create(void);
void orca_gcode_viewer_destroy(OrcaGcodeViewer* viewer);
int orca_gcode_viewer_init(OrcaGcodeViewer* viewer);
int orca_gcode_viewer_shutdown(OrcaGcodeViewer* viewer);
int orca_gcode_viewer_load_gcode(OrcaGcodeViewer* viewer, const char* gcode);
void orca_set_gcode_preview_generation(OrcaEngine* engine, long generation);
int orca_gcode_viewer_load_latest_slice(OrcaGcodeViewer* viewer, OrcaEngine* engine, long min_layer, long max_layer, int lod_hint, long generation);
const char* orca_gcode_preview_suggest_layer_ranges(OrcaEngine* engine, long min_layer, long max_layer, long vertex_budget);
int orca_gcode_viewer_render(OrcaGcodeViewer* viewer, const float* view_matrix, const float* projection_matrix);
long orca_gcode_viewer_get_layers_count(OrcaGcodeViewer* viewer);
int orca_gcode_viewer_set_layers_view_range(OrcaGcodeViewer* viewer, long min_layer, long max_layer);
int orca_gcode_viewer_set_extrusion_width_scale(OrcaGcodeViewer* viewer, float scale);
int orca_gcode_viewer_set_path_visibility(OrcaGcodeViewer* viewer, int kind, int id, int visible);
int orca_gcode_viewer_set_view_type(OrcaGcodeViewer* viewer, int view_type);
const char* orca_gcode_viewer_get_last_error(OrcaGcodeViewer* viewer);
const char* orca_gcode_viewer_get_last_load_metrics(OrcaGcodeViewer* viewer);

#ifdef __cplusplus
}
#endif

#endif
