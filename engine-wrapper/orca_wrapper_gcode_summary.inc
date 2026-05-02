static double parse_first_number(std::string_view value, bool* found = nullptr)
{
    if (found != nullptr) {
        *found = false;
    }
    for (size_t i = 0; i < value.size(); ++i) {
        const char ch = value[i];
        const bool number_start =
            (ch >= '0' && ch <= '9') ||
            ((ch == '-' || ch == '+') && i + 1 < value.size() && value[i + 1] >= '0' && value[i + 1] <= '9') ||
            (ch == '.' && i + 1 < value.size() && value[i + 1] >= '0' && value[i + 1] <= '9');
        if (!number_start) {
            continue;
        }
        const std::string tail(value.substr(i));
        char* end = nullptr;
        const double parsed = std::strtod(tail.c_str(), &end);
        if (end != tail.c_str()) {
            if (found != nullptr) {
                *found = true;
            }
            return parsed;
        }
    }
    return 0.0;
}

static double parse_number_sum(std::string_view value, bool* found = nullptr)
{
    if (found != nullptr) {
        *found = false;
    }
    double sum = 0.0;
    for (size_t i = 0; i < value.size(); ++i) {
        const char ch = value[i];
        const bool number_start =
            (ch >= '0' && ch <= '9') ||
            ((ch == '-' || ch == '+') && i + 1 < value.size() && value[i + 1] >= '0' && value[i + 1] <= '9') ||
            (ch == '.' && i + 1 < value.size() && value[i + 1] >= '0' && value[i + 1] <= '9');
        if (!number_start) {
            continue;
        }
        const std::string tail(value.substr(i));
        char* end = nullptr;
        const double parsed = std::strtod(tail.c_str(), &end);
        if (end == tail.c_str()) {
            continue;
        }
        sum += parsed;
        if (found != nullptr) {
            *found = true;
        }
        i += static_cast<size_t>(end - tail.c_str());
    }
    return sum;
}

static bool is_filament_used_line(std::string_view line, const std::string& lower, const char* unit)
{
    if (lower.find("filament used") == std::string::npos) {
        return false;
    }
    std::string unit_marker = "[";
    unit_marker += unit;
    unit_marker += "]";
    if (lower.find(unit_marker) == std::string::npos) {
        return false;
    }
    return line.find_first_of(":=") != std::string_view::npos;
}

static double parse_filament_used_value(std::string_view line, bool* found)
{
    if (found != nullptr) {
        *found = false;
    }
    const size_t separator = line.find_first_of(":=");
    if (separator == std::string_view::npos || separator + 1 >= line.size()) {
        return 0.0;
    }
    return parse_number_sum(line.substr(separator + 1), found);
}

static std::string format_duration_seconds(long seconds)
{
    if (seconds < 0) {
        seconds = 0;
    }
    const long hours = seconds / 3600;
    const long minutes = (seconds % 3600) / 60;
    const long remaining_seconds = seconds % 60;
    std::ostringstream output;
    if (hours > 0) {
        output << hours << "h " << std::setw(2) << std::setfill('0') << minutes << "m";
    } else if (minutes > 0) {
        output << minutes << "m " << std::setw(2) << std::setfill('0') << remaining_seconds << "s";
    } else {
        output << remaining_seconds << "s";
    }
    return output.str();
}

static bool is_reasonable_print_time_seconds(double seconds)
{
    return std::isfinite(seconds) && seconds > 0.0 && seconds <= static_cast<double>(kMaxReasonablePrintTimeSeconds);
}

static std::optional<long> parse_duration_text_seconds(std::string_view text)
{
    const std::string lower = lowercase_copy(text);
    double total_seconds = 0.0;
    bool parsed_unit = false;
    size_t cursor = 0;
    while (cursor < lower.size()) {
        while (cursor < lower.size() &&
               std::isspace(static_cast<unsigned char>(lower[cursor])) != 0) {
            ++cursor;
        }
        if (cursor >= lower.size()) {
            break;
        }
        const char* number_start = lower.c_str() + cursor;
        char* number_end = nullptr;
        const double value = std::strtod(number_start, &number_end);
        if (number_end == number_start) {
            ++cursor;
            continue;
        }
        cursor += static_cast<size_t>(number_end - number_start);
        while (cursor < lower.size() &&
               std::isspace(static_cast<unsigned char>(lower[cursor])) != 0) {
            ++cursor;
        }
        if (cursor >= lower.size()) {
            continue;
        }
        if (lower[cursor] == 'd') {
            total_seconds += value * 24.0 * 60.0 * 60.0;
            parsed_unit = true;
        } else if (lower[cursor] == 'h') {
            total_seconds += value * 60.0 * 60.0;
            parsed_unit = true;
        } else if (lower[cursor] == 'm') {
            total_seconds += value * 60.0;
            parsed_unit = true;
        } else if (lower[cursor] == 's') {
            total_seconds += value;
            parsed_unit = true;
        }
        ++cursor;
    }
    if (!parsed_unit || !std::isfinite(total_seconds) || total_seconds <= 0.0) {
        return std::nullopt;
    }
    return static_cast<long>(std::lround(total_seconds));
}

static long elapsed_ms_since(std::chrono::steady_clock::time_point start)
{
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start
    ).count();
}

static std::string parse_gcode_estimated_print_time(std::string_view line)
{
    const std::string lower = lowercase_copy(line);
    bool found_number = false;
    if (lower.rfind(";time:", 0) == 0) {
        const double seconds = parse_first_number(line.substr(6), &found_number);
        if (found_number && is_reasonable_print_time_seconds(seconds)) {
            return format_duration_seconds(static_cast<long>(seconds));
        }
        return {};
    }
    if (lower.find("estimated") == std::string::npos || lower.find("time") == std::string::npos) {
        return {};
    }
    const size_t separator = line.find_first_of(":=");
    if (separator == std::string_view::npos || separator + 1 >= line.size()) {
        return {};
    }
    std::string text = trim_copy(line.substr(separator + 1));
    std::replace(text.begin(), text.end(), '|', ' ');
    const std::optional<long> parsed_seconds = parse_duration_text_seconds(text);
    if (parsed_seconds && !is_reasonable_print_time_seconds(static_cast<double>(*parsed_seconds))) {
        return {};
    }
    return text;
}

static double parse_gcode_filament_grams(std::string_view line, bool* found)
{
    if (found != nullptr) {
        *found = false;
    }
    const std::string lower = lowercase_copy(line);
    if (!is_filament_used_line(line, lower, "g")) {
        return 0.0;
    }
    return parse_filament_used_value(line, found);
}

static double parse_gcode_filament_usage(std::string_view line, const char* unit, bool* found)
{
    if (found != nullptr) {
        *found = false;
    }
    const std::string lower = lowercase_copy(line);
    if (!is_filament_used_line(line, lower, unit)) {
        return 0.0;
    }
    return parse_filament_used_value(line, found);
}

struct GcodeMoveWords {
    double x{0.0};
    double y{0.0};
    double z{0.0};
    double e{0.0};
    double a{0.0};
    double f{0.0};
    double i{0.0};
    double j{0.0};
    bool has_x{false};
    bool has_y{false};
    bool has_z{false};
    bool has_e{false};
    bool has_a{false};
    bool has_f{false};
    bool has_i{false};
    bool has_j{false};
};

static bool is_gcode_number_char(char ch)
{
    return (ch >= '0' && ch <= '9') ||
        ch == '+' ||
        ch == '-' ||
        ch == '.' ||
        ch == 'e' ||
        ch == 'E';
}

static double parse_gcode_word_value(std::string_view command, size_t number_start, char** end)
{
    const char* source = command.data() + number_start;
    size_t number_length = 0;
    while (number_start + number_length < command.size() &&
        is_gcode_number_char(command[number_start + number_length])) {
        ++number_length;
    }
    if (number_length == 0) {
        *end = const_cast<char*>(source);
        return 0.0;
    }

    std::array<char, 96> stack_buffer{};
    if (number_length < stack_buffer.size()) {
        std::memcpy(stack_buffer.data(), source, number_length);
        stack_buffer[number_length] = '\0';
        char* parsed_end = nullptr;
        const double value = std::strtod(stack_buffer.data(), &parsed_end);
        *end = const_cast<char*>(source) + (parsed_end - stack_buffer.data());
        return value;
    }

    const std::string tail(command.substr(number_start, number_length));
    char* parsed_end = nullptr;
    const double value = std::strtod(tail.c_str(), &parsed_end);
    *end = const_cast<char*>(source) + (parsed_end - tail.c_str());
    return value;
}

static bool parse_gcode_move_words(std::string_view command, GcodeMoveWords& words)
{
    words = GcodeMoveWords{};
    bool parsed_any = false;
    for (size_t i = 0; i < command.size(); ++i) {
        const char letter = static_cast<char>(std::toupper(static_cast<unsigned char>(command[i])));
        if (letter != 'X' && letter != 'Y' && letter != 'Z' && letter != 'E' && letter != 'A' && letter != 'F' &&
            letter != 'I' && letter != 'J') {
            continue;
        }
        size_t number_start = i + 1;
        while (number_start < command.size() && std::isspace(static_cast<unsigned char>(command[number_start])) != 0) {
            ++number_start;
        }
        if (number_start >= command.size()) {
            continue;
        }
        char* end = nullptr;
        const double value = parse_gcode_word_value(command, number_start, &end);
        if (end == command.data() + number_start) {
            continue;
        }
        switch (letter) {
            case 'X': words.x = value; words.has_x = true; break;
            case 'Y': words.y = value; words.has_y = true; break;
            case 'Z': words.z = value; words.has_z = true; break;
            case 'E': words.e = value; words.has_e = true; break;
            case 'A': words.a = value; words.has_a = true; break;
            case 'F': words.f = value; words.has_f = true; break;
            case 'I': words.i = value; words.has_i = true; break;
            case 'J': words.j = value; words.has_j = true; break;
            default: break;
        }
        parsed_any = true;
        i = static_cast<size_t>(end - command.data());
    }
    return parsed_any;
}

static std::optional<double> parse_gcode_word_value(std::string_view command, char target)
{
    const char normalized_target = static_cast<char>(std::toupper(static_cast<unsigned char>(target)));
    for (size_t i = 0; i < command.size(); ++i) {
        const char letter = static_cast<char>(std::toupper(static_cast<unsigned char>(command[i])));
        if (letter != normalized_target) {
            continue;
        }
        size_t number_start = i + 1;
        while (number_start < command.size() && std::isspace(static_cast<unsigned char>(command[number_start])) != 0) {
            ++number_start;
        }
        if (number_start >= command.size()) {
            continue;
        }
        const std::string tail(command.substr(number_start));
        char* end = nullptr;
        const double value = std::strtod(tail.c_str(), &end);
        if (end != tail.c_str()) {
            return value;
        }
    }
    return std::nullopt;
}

static double estimate_trapezoid_move_seconds(double distance_mm, double feedrate_mm_per_min, double acceleration_mm_per_s2)
{
    if (distance_mm <= 0.0 || feedrate_mm_per_min <= 0.0) {
        return 0.0;
    }
    const double speed_mm_per_s = feedrate_mm_per_min / 60.0;
    const double acceleration = std::max(1.0, acceleration_mm_per_s2);
    const double accelerate_and_decelerate_distance = (speed_mm_per_s * speed_mm_per_s) / acceleration;
    if (distance_mm >= accelerate_and_decelerate_distance) {
        return (distance_mm / speed_mm_per_s) + (speed_mm_per_s / acceleration);
    }
    return 2.0 * std::sqrt(distance_mm / acceleration);
}

static double estimate_arc_distance_mm(
    bool clockwise,
    const GcodeMoveWords& words,
    double previous_x,
    double previous_y,
    double previous_z,
    double x,
    double y,
    double z)
{
    if (!words.has_i && !words.has_j) {
        const double dx = x - previous_x;
        const double dy = y - previous_y;
        const double dz = z - previous_z;
        return std::sqrt(dx * dx + dy * dy + dz * dz);
    }
    const double center_x = previous_x + (words.has_i ? words.i : 0.0);
    const double center_y = previous_y + (words.has_j ? words.j : 0.0);
    const double radius = std::hypot(previous_x - center_x, previous_y - center_y);
    if (radius <= 0.0) {
        return 0.0;
    }
    const double start_angle = std::atan2(previous_y - center_y, previous_x - center_x);
    const double end_angle = std::atan2(y - center_y, x - center_x);
    double sweep = clockwise ? start_angle - end_angle : end_angle - start_angle;
    while (sweep <= 0.0) {
        sweep += 2.0 * M_PI;
    }
    const double planar_distance = radius * sweep;
    const double dz = z - previous_z;
    return std::sqrt(planar_distance * planar_distance + dz * dz);
}

static libvgcode::Vec3 interpolate_arc_position(
    bool clockwise,
    const GcodeMoveWords& words,
    double previous_x,
    double previous_y,
    double previous_z,
    double x,
    double y,
    double z,
    double fraction)
{
    fraction = std::clamp(fraction, 0.0, 1.0);
    if (!words.has_i && !words.has_j) {
        return {
            static_cast<float>(previous_x + (x - previous_x) * fraction),
            static_cast<float>(previous_y + (y - previous_y) * fraction),
            static_cast<float>(previous_z + (z - previous_z) * fraction)
        };
    }
    const double center_x = previous_x + (words.has_i ? words.i : 0.0);
    const double center_y = previous_y + (words.has_j ? words.j : 0.0);
    const double radius = std::hypot(previous_x - center_x, previous_y - center_y);
    if (radius <= 0.0) {
        return {
            static_cast<float>(previous_x + (x - previous_x) * fraction),
            static_cast<float>(previous_y + (y - previous_y) * fraction),
            static_cast<float>(previous_z + (z - previous_z) * fraction)
        };
    }
    const double start_angle = std::atan2(previous_y - center_y, previous_x - center_x);
    const double end_angle = std::atan2(y - center_y, x - center_x);
    double sweep = clockwise ? start_angle - end_angle : end_angle - start_angle;
    while (sweep <= 0.0) {
        sweep += 2.0 * M_PI;
    }
    const double angle = clockwise
        ? start_angle - sweep * fraction
        : start_angle + sweep * fraction;
    return {
        static_cast<float>(center_x + std::cos(angle) * radius),
        static_cast<float>(center_y + std::sin(angle) * radius),
        static_cast<float>(previous_z + (z - previous_z) * fraction)
    };
}

static std::string join_summary_list(const std::vector<std::string>& values)
{
    std::ostringstream output;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            output << ',';
        }
        std::string value = values[i];
        std::replace(value.begin(), value.end(), '|', ' ');
        std::replace(value.begin(), value.end(), ',', ' ');
        output << value;
    }
    return output.str();
}

static std::string replace_summary_field(std::string summary, std::string_view key, const std::string& value)
{
    const std::string prefix = std::string(key) + "=";
    size_t field_start = 0;
    while (field_start <= summary.size()) {
        const size_t field_end = summary.find('|', field_start);
        const size_t field_length = (field_end == std::string::npos ? summary.size() : field_end) - field_start;
        if (summary.compare(field_start, prefix.size(), prefix) == 0) {
            summary.replace(field_start + prefix.size(), field_length - prefix.size(), value);
            return summary;
        }
        if (field_end == std::string::npos) {
            break;
        }
        field_start = field_end + 1;
    }
    if (!summary.empty()) {
        summary.push_back('|');
    }
    summary += prefix;
    summary += value;
    return summary;
}

static std::string summarize_gcode_for_android(std::istream& input, uintmax_t byte_count)
{
    constexpr double default_density_g_per_cm3 = 1.24;
    size_t line_count = 0;
    size_t layer_change_count = 0;
    std::vector<std::string> observed_types;
    std::vector<std::string> wall_shell_types;
    std::string estimated_print_time;
    double total_filament_grams = 0.0;
    double per_tool_filament_grams = 0.0;
    double wipe_tower_filament_grams = 0.0;
    double filament_mm = 0.0;
    double wipe_tower_filament_mm = 0.0;
    double filament_cm3 = 0.0;
    double wipe_tower_filament_cm3 = 0.0;
    double filament_diameter_mm = 1.75;
    double filament_density_g_per_cm3 = default_density_g_per_cm3;
    bool has_total_filament_grams = false;
    bool has_per_tool_filament_grams = false;
    bool has_wipe_tower_filament_grams = false;
    bool has_filament_mm = false;
    bool has_wipe_tower_filament_mm = false;
    bool has_filament_cm3 = false;
    bool has_wipe_tower_filament_cm3 = false;
    double positive_extrusion_mm = 0.0;
    double estimated_seconds_from_moves = 0.0;
    double acceleration_mm_per_s2 = 10000.0;
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    double e = 0.0;
    double a = 0.0;
    double feedrate_mm_per_min = 0.0;
    bool absolute_xyz = true;
    bool absolute_e = true;
    GcodeMoveWords words;

    std::string raw_line;
    while (std::getline(input, raw_line)) {
        std::string_view line(raw_line);
        if (!line.empty() && line.back() == '\r') {
            line.remove_suffix(1);
        }
        ++line_count;

        if (!line.empty() && line.front() == ';') {
            if (starts_with_case_insensitive(line, ";LAYER_CHANGE") ||
                starts_with_case_insensitive(line, "; CHANGE_LAYER")) {
                ++layer_change_count;
            } else if (starts_with_case_insensitive(line, ";TYPE:")) {
                std::string label = trim_copy(line.substr(6));
                if (!label.empty()) {
                    append_unique_limited(observed_types, label, 8);
                    const std::string lower_label = lowercase_copy(label);
                    if (lower_label.find("wall") != std::string::npos ||
                        lower_label.find("surface") != std::string::npos ||
                        lower_label.find("shell") != std::string::npos) {
                        append_unique_limited(wall_shell_types, label, 6);
                    }
                }
            }
            if (estimated_print_time.empty()) {
                estimated_print_time = parse_gcode_estimated_print_time(line);
            }
            const std::string lower_line = lowercase_copy(line);
            bool found = false;
            const double grams = parse_gcode_filament_grams(line, &found);
            if (found) {
                if (lower_line.find("total filament used") != std::string::npos) {
                    total_filament_grams = grams;
                    has_total_filament_grams = true;
                } else if (lower_line.find("for wipe tower") != std::string::npos) {
                    wipe_tower_filament_grams = grams;
                    has_wipe_tower_filament_grams = true;
                } else if (!has_per_tool_filament_grams) {
                    per_tool_filament_grams = grams;
                    has_per_tool_filament_grams = true;
                }
            }
            const double parsed_mm = parse_gcode_filament_usage(line, "mm", &found);
            if (found) {
                if (lower_line.find("for wipe tower") != std::string::npos) {
                    if (!has_wipe_tower_filament_mm) {
                        wipe_tower_filament_mm = parsed_mm;
                        has_wipe_tower_filament_mm = true;
                    }
                } else if (!has_filament_mm) {
                    filament_mm = parsed_mm;
                    has_filament_mm = true;
                }
            }
            const double parsed_cm3 = parse_gcode_filament_usage(line, "cm3", &found);
            if (found) {
                if (lower_line.find("for wipe tower") != std::string::npos) {
                    if (!has_wipe_tower_filament_cm3) {
                        wipe_tower_filament_cm3 = parsed_cm3;
                        has_wipe_tower_filament_cm3 = true;
                    }
                } else if (!has_filament_cm3) {
                    filament_cm3 = parsed_cm3;
                    has_filament_cm3 = true;
                }
            }
            if (lower_line.rfind("; filament_diameter:", 0) == 0) {
                const double parsed = parse_first_number(line, &found);
                if (found && parsed > 0.0) {
                    filament_diameter_mm = parsed;
                }
            }
            if (lower_line.rfind("; filament_density:", 0) == 0) {
                const double parsed = parse_first_number(line, &found);
                if (found && parsed > 0.0) {
                    filament_density_g_per_cm3 = parsed;
                }
            }
        } else {
            size_t command_start = 0;
            while (command_start < line.size() && std::isspace(static_cast<unsigned char>(line[command_start])) != 0) {
                ++command_start;
            }
            std::string_view command = line.substr(command_start);
            const size_t comment_pos = command.find(';');
            if (comment_pos != std::string_view::npos) {
                command = command.substr(0, comment_pos);
            }
            if (command_matches(command, "G90")) {
                absolute_xyz = true;
            } else if (command_matches(command, "G91")) {
                absolute_xyz = false;
            } else if (command_matches(command, "M82")) {
                absolute_e = true;
            } else if (command_matches(command, "M83")) {
                absolute_e = false;
            } else if (command_matches(command, "M204")) {
                auto acceleration = parse_gcode_word_value(command, 'S');
                if (!acceleration) {
                    acceleration = parse_gcode_word_value(command, 'P');
                }
                if (!acceleration) {
                    acceleration = parse_gcode_word_value(command, 'T');
                }
                if (acceleration && *acceleration > 0.0) {
                    acceleration_mm_per_s2 = *acceleration;
                }
            } else if (command_matches(command, "G92")) {
                if (parse_gcode_move_words(command, words)) {
                    if (words.has_x) x = words.x;
                    if (words.has_y) y = words.y;
                    if (words.has_z) z = words.z;
                    if (words.has_e) e = words.e;
                    if (words.has_a) a = words.a;
                }
            } else if (command_matches(command, "G0") || command_matches(command, "G1") ||
                       command_matches(command, "G2") || command_matches(command, "G3")) {
                if (parse_gcode_move_words(command, words)) {
                    const bool is_arc = command_matches(command, "G2") || command_matches(command, "G3");
                    const double previous_x = x;
                    const double previous_y = y;
                    const double previous_z = z;
                    const double previous_e = e;
                    const double previous_a = a;
                    if (words.has_x) x = absolute_xyz ? words.x : x + words.x;
                    if (words.has_y) y = absolute_xyz ? words.y : y + words.y;
                    if (words.has_z) z = absolute_xyz ? words.z : z + words.z;
                    if (words.has_e) e = absolute_e ? words.e : e + words.e;
                    if (words.has_a) a = absolute_e ? words.a : a + words.a;
                    if (words.has_f) feedrate_mm_per_min = words.f;
                    const double de = words.has_e ? e - previous_e : (words.has_a ? a - previous_a : 0.0);
                    if (de > 0.0) {
                        positive_extrusion_mm += de;
                    }
                    if (estimated_print_time.empty()) {
                        const double dx = x - previous_x;
                        const double dy = y - previous_y;
                        const double dz = z - previous_z;
                        const double distance_mm = is_arc
                            ? estimate_arc_distance_mm(command_matches(command, "G2"), words, previous_x, previous_y, previous_z, x, y, z)
                            : std::sqrt(dx * dx + dy * dy + dz * dz);
                        const double timed_distance_mm = distance_mm > 0.0 ? distance_mm : std::abs(de);
                        if (feedrate_mm_per_min > 0.0 && timed_distance_mm > 0.0) {
                            estimated_seconds_from_moves += is_arc
                                ? timed_distance_mm / (feedrate_mm_per_min / 60.0)
                                : estimate_trapezoid_move_seconds(
                                    timed_distance_mm,
                                    feedrate_mm_per_min,
                                    acceleration_mm_per_s2);
                        }
                    }
                }
            }
        }

    }

    if (estimated_print_time.empty() && estimated_seconds_from_moves > 0.0) {
        estimated_print_time = format_duration_seconds(static_cast<long>(estimated_seconds_from_moves));
    }

    double filament_grams = 0.0;
    bool has_filament_grams = false;
    if (has_total_filament_grams) {
        filament_grams = total_filament_grams;
        has_filament_grams = true;
    } else if (has_per_tool_filament_grams) {
        filament_grams = per_tool_filament_grams + (has_wipe_tower_filament_grams ? wipe_tower_filament_grams : 0.0);
        has_filament_grams = true;
    } else if (has_filament_cm3) {
        filament_grams = (filament_cm3 + (has_wipe_tower_filament_cm3 ? wipe_tower_filament_cm3 : 0.0)) *
            filament_density_g_per_cm3;
        has_filament_grams = true;
    } else if (has_filament_mm || positive_extrusion_mm > 0.0) {
        const double filament_length_mm = has_filament_mm
            ? filament_mm + (has_wipe_tower_filament_mm ? wipe_tower_filament_mm : 0.0)
            : positive_extrusion_mm;
        const double radius_mm = filament_diameter_mm / 2.0;
        const double volume_mm3 = filament_length_mm * M_PI * radius_mm * radius_mm;
        filament_grams = volume_mm3 * filament_density_g_per_cm3 / 1000.0;
        has_filament_grams = filament_grams >= 0.005;
    }

    std::ostringstream summary;
    summary << "bytes=" << byte_count
            << "|lines=" << line_count
            << "|layers=" << layer_change_count
            << "|time=" << estimated_print_time
            << "|grams=";
    if (has_filament_grams) {
        summary << std::fixed << std::setprecision(5) << filament_grams;
    }
    summary << "|types=" << join_summary_list(observed_types)
            << "|walls=" << join_summary_list(wall_shell_types);
    return summary.str();
}

static std::string summarize_gcode_for_android(const std::string& gcode)
{
    std::istringstream input(gcode);
    return summarize_gcode_for_android(input, gcode.size());
}

static std::string summarize_gcode_file_for_android(const std::filesystem::path& path)
{
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return {};
    }
    std::error_code ec;
    const uintmax_t byte_count = std::filesystem::file_size(path, ec);
    return summarize_gcode_for_android(input, ec ? 0 : byte_count);
}
