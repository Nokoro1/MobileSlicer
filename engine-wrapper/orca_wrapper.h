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
int orca_plan_plate_arrangement(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count, const char* config_json, int allow_rotation, double* out_transforms);
int orca_plan_auto_orientation(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count, const char* config_json, double* out_transforms);
int orca_set_model_placement(OrcaEngine* engine, double x_mm, double y_mm, double z_mm);
int orca_set_model_transform(OrcaEngine* engine, double x_mm, double y_mm, double z_mm, double rotation_x_radians, double rotation_y_radians, double rotation_z_radians, double uniform_scale);
int orca_set_config_json(OrcaEngine* engine, const char* json);
int orca_slice(OrcaEngine* engine);
void orca_clear_generated_gcode(OrcaEngine* engine);

const char* orca_get_gcode(OrcaEngine* engine);
const char* orca_get_gcode_summary(OrcaEngine* engine);
const char* orca_get_enriched_gcode_summary(OrcaEngine* engine);
const char* orca_get_slice_metrics(OrcaEngine* engine);
int orca_write_gcode_to_file(OrcaEngine* engine, const char* path);
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
