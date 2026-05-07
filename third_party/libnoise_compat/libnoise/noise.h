#ifndef MOBILE_SLICER_LIBNOISE_COMPAT_NOISE_H
#define MOBILE_SLICER_LIBNOISE_COMPAT_NOISE_H

#include <cmath>

namespace noise {
namespace module {

class Module {
public:
    explicit Module(int source_module_count) : m_source_module_count(source_module_count) {}
    virtual ~Module() = default;

    virtual int GetSourceModuleCount() const = 0;
    virtual double GetValue(double x, double y, double z) const = 0;

protected:
    int m_source_module_count;
};

class FractalNoiseBase : public Module {
public:
    FractalNoiseBase() : Module(0) {}

    int GetSourceModuleCount() const override { return 0; }

    void SetFrequency(double value) { m_frequency = value; }
    void SetOctaveCount(int value) { m_octaves = value < 1 ? 1 : value; }
    void SetPersistence(double value) { m_persistence = value; }
    void SetSeed(int value) { m_seed = value; }

protected:
    double base_noise(double x, double y, double z) const
    {
        double total = 0.0;
        double amplitude = 1.0;
        double frequency = m_frequency <= 0.0 ? 1.0 : m_frequency;
        for (int octave = 0; octave < m_octaves; ++octave) {
            total += amplitude * std::sin((x + m_seed + octave * 13.0) * frequency)
                           * std::cos((y - m_seed - octave * 7.0) * frequency)
                           * std::sin((z + octave * 3.0) * frequency);
            amplitude *= m_persistence;
            frequency *= 2.0;
        }
        return total;
    }

    double m_frequency = 1.0;
    int m_octaves = 1;
    double m_persistence = 0.5;
    int m_seed = 0;
};

class Perlin : public FractalNoiseBase {
public:
    double GetValue(double x, double y, double z) const override { return base_noise(x, y, z); }
};

class Billow : public FractalNoiseBase {
public:
    double GetValue(double x, double y, double z) const override
    {
        return std::abs(base_noise(x, y, z)) * 2.0 - 1.0;
    }
};

class RidgedMulti : public FractalNoiseBase {
public:
    double GetValue(double x, double y, double z) const override
    {
        return 1.0 - std::abs(base_noise(x, y, z));
    }
};

class Voronoi : public Module {
public:
    Voronoi() : Module(0) {}

    int GetSourceModuleCount() const override { return 0; }

    void SetFrequency(double value) { m_frequency = value; }
    void SetDisplacement(double value) { m_displacement = value; }
    void SetSeed(int value) { m_seed = value; }

    double GetValue(double x, double y, double z) const override
    {
        const double cell_x = std::floor((x + m_seed) * m_frequency);
        const double cell_y = std::floor((y - m_seed) * m_frequency);
        const double cell_z = std::floor((z + m_seed * 0.5) * m_frequency);
        const double n = std::sin(cell_x * 12.9898 + cell_y * 78.233 + cell_z * 37.719);
        return n * m_displacement;
    }

private:
    double m_frequency = 1.0;
    double m_displacement = 1.0;
    int m_seed = 0;
};

} // namespace module
} // namespace noise

#endif
