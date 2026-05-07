#ifndef ORCA_WRAPPER_PAINT_REPLAY_TYPES_H
#define ORCA_WRAPPER_PAINT_REPLAY_TYPES_H

// Private implementation header. Include through orca_wrapper_module_context.h.
struct PaintTriangleReplay {
    int triangle_index{-1};
    std::string hex_bits;
};

struct PaintVolumeReplay {
    int volume_index{-1};
    int triangle_count{0};
    std::string mesh_fingerprint;
    std::vector<PaintTriangleReplay> triangles;
};

struct PaintLayerReplay {
    std::string mode;
    std::vector<int> color_slots;
    std::vector<PaintVolumeReplay> volumes;
};

struct PaintObjectReplay {
    long long mobile_object_id{0};
    std::vector<PaintLayerReplay> layers;
};


#endif // ORCA_WRAPPER_PAINT_REPLAY_TYPES_H
