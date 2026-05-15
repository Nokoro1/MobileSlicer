#include "orca_wrapper_utils.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <functional>
#include <regex>
#include <sstream>
#include <unordered_map>

namespace mobileslicer::orca_wrapper {

std::string trim_copy(std::string value)
{
    const auto first = std::find_if_not(value.begin(), value.end(), [](unsigned char ch) { return std::isspace(ch) != 0; });
    const auto last = std::find_if_not(value.rbegin(), value.rend(), [](unsigned char ch) { return std::isspace(ch) != 0; }).base();
    if (first >= last) {
        return {};
    }
    return std::string(first, last);
}

std::string trim_copy(std::string_view value)
{
    size_t first = 0;
    while (first < value.size() && std::isspace(static_cast<unsigned char>(value[first])) != 0) {
        ++first;
    }
    size_t last = value.size();
    while (last > first && std::isspace(static_cast<unsigned char>(value[last - 1])) != 0) {
        --last;
    }
    return std::string(value.substr(first, last - first));
}

std::string_view trim_view(std::string_view value)
{
    size_t first = 0;
    while (first < value.size() && std::isspace(static_cast<unsigned char>(value[first])) != 0) {
        ++first;
    }
    size_t last = value.size();
    while (last > first && std::isspace(static_cast<unsigned char>(value[last - 1])) != 0) {
        --last;
    }
    return value.substr(first, last - first);
}

bool starts_with_case_insensitive(std::string_view value, const char* prefix)
{
    const size_t prefix_size = std::strlen(prefix);
    if (value.size() < prefix_size) {
        return false;
    }
    for (size_t i = 0; i < prefix_size; ++i) {
        if (std::tolower(static_cast<unsigned char>(value[i])) !=
            std::tolower(static_cast<unsigned char>(prefix[i]))) {
            return false;
        }
    }
    return true;
}

bool command_matches(std::string_view command, const char* expected)
{
    command = trim_view(command);
    const size_t expected_size = std::strlen(expected);
    if (command.size() < expected_size) {
        return false;
    }
    for (size_t i = 0; i < expected_size; ++i) {
        if (std::tolower(static_cast<unsigned char>(command[i])) !=
            std::tolower(static_cast<unsigned char>(expected[i]))) {
            return false;
        }
    }
    return command.size() == expected_size ||
        std::isspace(static_cast<unsigned char>(command[expected_size])) != 0;
}

bool is_preview_ignored_command(std::string_view command)
{
    // Bambu-specific calibration moves carry XYZ/E words but are not printable
    // toolpath. Keep them out of the phone preview even if a lower parser treats
    // them as movement-like commands.
    return command_matches(command, "G130");
}

std::string lowercase_copy(std::string_view value)
{
    std::string result(value);
    std::transform(result.begin(), result.end(), result.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return result;
}

void append_unique_limited(std::vector<std::string>& values, std::string value, size_t limit)
{
    if (value.empty() || values.size() >= limit) {
        return;
    }
    if (std::find(values.begin(), values.end(), value) == values.end()) {
        values.emplace_back(std::move(value));
    }
}

bool has_stl_extension(const std::string& path)
{
    const std::filesystem::path file_path(path);
    std::string extension = file_path.extension().string();
    std::transform(extension.begin(), extension.end(), extension.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return extension == ".stl";
}

std::string unescape_json_string(std::string_view value)
{
    std::string output;
    output.reserve(value.size());
    bool escaped = false;
    for (const char ch : value) {
        if (escaped) {
            switch (ch) {
                case '"': output.push_back('"'); break;
                case '\\': output.push_back('\\'); break;
                case '/': output.push_back('/'); break;
                case 'b': output.push_back('\b'); break;
                case 'f': output.push_back('\f'); break;
                case 'n': output.push_back('\n'); break;
                case 'r': output.push_back('\r'); break;
                case 't': output.push_back('\t'); break;
                default: output.push_back(ch); break;
            }
            escaped = false;
            continue;
        }
        if (ch == '\\') {
            escaped = true;
            continue;
        }
        output.push_back(ch);
    }
    return output;
}

struct JsonScalarIndex {
    const std::string* source{nullptr};
    size_t source_size{0};
    size_t source_hash{0};
    std::unordered_map<std::string, std::string_view> values;
};

thread_local JsonScalarIndex g_json_scalar_index;

static void skip_json_whitespace(const std::string& json, size_t& cursor)
{
    while (cursor < json.size() && std::isspace(static_cast<unsigned char>(json[cursor])) != 0) {
        ++cursor;
    }
}

static bool skip_json_string(const std::string& json, size_t& cursor)
{
    if (cursor >= json.size() || json[cursor] != '"') {
        return false;
    }
    ++cursor;
    bool escaped = false;
    for (; cursor < json.size(); ++cursor) {
        const char ch = json[cursor];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (ch == '\\') {
            escaped = true;
            continue;
        }
        if (ch == '"') {
            ++cursor;
            return true;
        }
    }
    return false;
}

static bool skip_json_value(const std::string& json, size_t& cursor)
{
    skip_json_whitespace(json, cursor);
    if (cursor >= json.size()) {
        return false;
    }
    if (json[cursor] == '"') {
        return skip_json_string(json, cursor);
    }
    if (json[cursor] == '{' || json[cursor] == '[') {
        const char open = json[cursor];
        const char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (; cursor < json.size(); ++cursor) {
            if (json[cursor] == '"') {
                if (!skip_json_string(json, cursor)) {
                    return false;
                }
                --cursor;
                continue;
            }
            if (json[cursor] == open) {
                ++depth;
            } else if (json[cursor] == close) {
                --depth;
                if (depth == 0) {
                    ++cursor;
                    return true;
                }
            }
        }
        return false;
    }
    while (cursor < json.size() && json[cursor] != ',' && json[cursor] != '}') {
        ++cursor;
    }
    return true;
}

static JsonScalarIndex& json_scalar_index(const std::string& json)
{
    JsonScalarIndex& index = g_json_scalar_index;
    const size_t content_hash = std::hash<std::string_view>{}(std::string_view(json));
    if (index.source == &json && index.source_size == json.size() && index.source_hash == content_hash) {
        return index;
    }

    index = JsonScalarIndex{};
    index.source = &json;
    index.source_size = json.size();
    index.source_hash = content_hash;

    size_t cursor = 0;
    skip_json_whitespace(json, cursor);
    if (cursor >= json.size() || json[cursor] != '{') {
        return index;
    }
    ++cursor;
    while (cursor < json.size()) {
        skip_json_whitespace(json, cursor);
        if (cursor >= json.size() || json[cursor] == '}') {
            break;
        }
        if (json[cursor] != '"') {
            break;
        }
        const size_t key_begin = cursor + 1;
        if (!skip_json_string(json, cursor)) {
            break;
        }
        const size_t key_end = cursor - 1;
        skip_json_whitespace(json, cursor);
        if (cursor >= json.size() || json[cursor] != ':') {
            break;
        }
        ++cursor;
        skip_json_whitespace(json, cursor);
        const size_t value_begin = cursor;
        if (!skip_json_value(json, cursor)) {
            break;
        }
        const size_t value_end = cursor;
        index.values.emplace(
            unescape_json_string(std::string_view(json).substr(key_begin, key_end - key_begin)),
            std::string_view(json).substr(value_begin, value_end - value_begin)
        );
        skip_json_whitespace(json, cursor);
        if (cursor < json.size() && json[cursor] == ',') {
            ++cursor;
        }
    }
    return index;
}

void invalidate_json_scalar_index()
{
    g_json_scalar_index = JsonScalarIndex{};
}

std::optional<std::string_view> indexed_json_value(const std::string& json, const std::string& key)
{
    JsonScalarIndex& index = json_scalar_index(json);
    const auto found = index.values.find(key);
    if (found == index.values.end()) {
        return std::nullopt;
    }
    return found->second;
}

std::optional<std::string_view> first_json_array_item(std::string_view raw)
{
    raw = trim_view(raw);
    if (raw.size() < 2 || raw.front() != '[' || raw.back() != ']') {
        return std::nullopt;
    }
    raw.remove_prefix(1);
    raw.remove_suffix(1);
    size_t cursor = 0;
    while (cursor < raw.size() && std::isspace(static_cast<unsigned char>(raw[cursor])) != 0) {
        ++cursor;
    }
    const size_t begin = cursor;
    if (cursor < raw.size() && raw[cursor] == '"') {
        ++cursor;
        bool escaped = false;
        for (; cursor < raw.size(); ++cursor) {
            if (escaped) {
                escaped = false;
            } else if (raw[cursor] == '\\') {
                escaped = true;
            } else if (raw[cursor] == '"') {
                return raw.substr(begin, cursor - begin + 1);
            }
        }
        return std::nullopt;
    }
    while (cursor < raw.size() && raw[cursor] != ',') {
        ++cursor;
    }
    return raw.substr(begin, cursor - begin);
}

std::optional<double> extract_number(const std::string& json, const std::string& key)
{
    if (const auto raw_value = indexed_json_value(json, key)) {
        std::string_view raw = *raw_value;
        if (const auto first_item = first_json_array_item(raw)) {
            raw = *first_item;
        }
        raw = trim_view(raw);
        if (raw.size() >= 2 && raw.front() == '"' && raw.back() == '"') {
            raw.remove_prefix(1);
            raw.remove_suffix(1);
        }
        const std::string raw_string(raw);
        char* end = nullptr;
        const double parsed = std::strtod(raw_string.c_str(), &end);
        if (end != raw_string.c_str()) {
            return parsed;
        }
        return std::nullopt;
    }
    const std::regex pattern("\"" + key + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    std::smatch match;
    if (!std::regex_search(json, match, pattern) || match.size() < 2) {
        return std::nullopt;
    }
    try {
        return std::stod(match[1].str());
    } catch (...) {
        return std::nullopt;
    }
}

std::optional<double> extract_number_any(const std::string& json, std::initializer_list<const char*> keys)
{
    for (const char* key : keys) {
        if (const auto value = extract_number(json, key)) {
            return value;
        }
    }
    return std::nullopt;
}

std::optional<bool> extract_bool(const std::string& json, const std::string& key)
{
    if (const auto raw_value = indexed_json_value(json, key)) {
        std::string raw = trim_copy(*raw_value);
        if (const auto first_item = first_json_array_item(raw)) {
            raw = trim_copy(*first_item);
        }
        if (raw.size() >= 2 && raw.front() == '"' && raw.back() == '"') {
            raw = unescape_json_string(std::string_view(raw).substr(1, raw.size() - 2));
        }
        const std::string lower = lowercase_copy(raw);
        if (lower == "true" || lower == "1") {
            return true;
        }
        if (lower == "false" || lower == "0") {
            return false;
        }
        return std::nullopt;
    }
    const std::regex pattern("\"" + key + "\"\\s*:\\s*(true|false)");
    std::smatch match;
    if (!std::regex_search(json, match, pattern) || match.size() < 2) {
        return std::nullopt;
    }
    return match[1].str() == "true";
}

std::optional<std::string> extract_string(const std::string& json, const std::string& key)
{
    if (const auto raw_value = indexed_json_value(json, key)) {
        std::string_view raw = *raw_value;
        if (const auto first_item = first_json_array_item(raw)) {
            raw = *first_item;
        }
        raw = trim_view(raw);
        if (raw.size() >= 2 && raw.front() == '"' && raw.back() == '"') {
            return unescape_json_string(raw.substr(1, raw.size() - 2));
        }
        if (!raw.empty() && raw != "null") {
            return std::string(raw);
        }
        return std::nullopt;
    }
    const std::string quoted_key = "\"" + key + "\"";
    const size_t key_pos = json.find(quoted_key);
    if (key_pos == std::string::npos) {
        return std::nullopt;
    }
    const size_t colon_pos = json.find(':', key_pos + quoted_key.size());
    if (colon_pos == std::string::npos) {
        return std::nullopt;
    }
    size_t cursor = colon_pos + 1;
    while (cursor < json.size() && std::isspace(static_cast<unsigned char>(json[cursor])) != 0) {
        ++cursor;
    }
    if (cursor >= json.size() || json[cursor] != '"') {
        return std::nullopt;
    }
    ++cursor;
    std::string value;
    value.reserve(32);
    bool escaped = false;
    for (; cursor < json.size(); ++cursor) {
        const char ch = json[cursor];
        if (escaped) {
            switch (ch) {
                case '"':
                case '\\':
                case '/':
                    value.push_back(ch);
                    break;
                case 'b':
                    value.push_back('\b');
                    break;
                case 'f':
                    value.push_back('\f');
                    break;
                case 'n':
                    value.push_back('\n');
                    break;
                case 'r':
                    value.push_back('\r');
                    break;
                case 't':
                    value.push_back('\t');
                    break;
                default:
                    value.push_back(ch);
                    break;
            }
            escaped = false;
            continue;
        }
        if (ch == '\\') {
            escaped = true;
            continue;
        }
        if (ch == '"') {
            return value;
        }
        value.push_back(ch);
    }
    return std::nullopt;
}

std::optional<std::string> extract_string_any(const std::string& json, std::initializer_list<const char*> keys)
{
    for (const char* key : keys) {
        if (const auto value = extract_string(json, key)) {
            return value;
        }
    }
    return std::nullopt;
}

std::optional<std::vector<std::string>> extract_string_vector_exact(const std::string& json, const std::string& key)
{
    const auto raw_value = indexed_json_value(json, key);
    if (!raw_value) {
        if (const auto scalar = extract_string(json, key)) {
            return std::vector<std::string>{*scalar};
        }
        return std::nullopt;
    }

    std::string_view raw = trim_view(*raw_value);
    if (raw.empty() || raw == "null") {
        return std::nullopt;
    }
    if (raw.size() >= 2 && raw.front() == '"' && raw.back() == '"') {
        return std::vector<std::string>{unescape_json_string(raw.substr(1, raw.size() - 2))};
    }
    if (raw.size() < 2 || raw.front() != '[' || raw.back() != ']') {
        return std::vector<std::string>{std::string(raw)};
    }

    raw.remove_prefix(1);
    raw.remove_suffix(1);
    std::vector<std::string> values;
    size_t cursor = 0;
    while (cursor < raw.size()) {
        while (cursor < raw.size() &&
               (std::isspace(static_cast<unsigned char>(raw[cursor])) != 0 || raw[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= raw.size()) {
            break;
        }
        if (raw[cursor] == '"') {
            ++cursor;
            std::string item;
            bool escaped = false;
            for (; cursor < raw.size(); ++cursor) {
                const char ch = raw[cursor];
                if (escaped) {
                    switch (ch) {
                        case '"': item.push_back('"'); break;
                        case '\\': item.push_back('\\'); break;
                        case '/': item.push_back('/'); break;
                        case 'b': item.push_back('\b'); break;
                        case 'f': item.push_back('\f'); break;
                        case 'n': item.push_back('\n'); break;
                        case 'r': item.push_back('\r'); break;
                        case 't': item.push_back('\t'); break;
                        default: item.push_back(ch); break;
                    }
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    ++cursor;
                    break;
                }
                item.push_back(ch);
            }
            values.push_back(item);
            continue;
        }
        const size_t begin = cursor;
        while (cursor < raw.size() && raw[cursor] != ',') {
            ++cursor;
        }
        const std::string item = trim_copy(raw.substr(begin, cursor - begin));
        values.push_back(item == "null" ? std::string() : item);
    }
    return values;
}

std::vector<double> parse_number_list(const std::string& value)
{
    std::vector<double> values;
    std::stringstream stream(value);
    std::string item;
    while (std::getline(stream, item, ',')) {
        item = trim_copy(item);
        if (item.empty()) {
            continue;
        }
        try {
            values.push_back(std::stod(item));
        } catch (...) {
            values.clear();
            return values;
        }
    }
    return values;
}

std::optional<std::string> extract_config_scalar_or_list_string(
    const std::string& json,
    const std::string& key,
    char list_separator)
{
    const auto raw_value = indexed_json_value(json, key);
    if (!raw_value) {
        return extract_string(json, key);
    }

    std::string_view raw = trim_view(*raw_value);
    if (raw.empty() || raw == "null") {
        return std::nullopt;
    }
    if (raw.size() >= 2 && raw.front() == '"' && raw.back() == '"') {
        return unescape_json_string(raw.substr(1, raw.size() - 2));
    }
    if (raw.size() < 2 || raw.front() != '[' || raw.back() != ']') {
        return std::string(raw);
    }

    raw.remove_prefix(1);
    raw.remove_suffix(1);
    std::vector<std::string> values;
    size_t cursor = 0;
    while (cursor < raw.size()) {
        while (cursor < raw.size() &&
               (std::isspace(static_cast<unsigned char>(raw[cursor])) != 0 || raw[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= raw.size()) {
            break;
        }
        if (raw[cursor] == '"') {
            ++cursor;
            std::string item;
            bool escaped = false;
            for (; cursor < raw.size(); ++cursor) {
                const char ch = raw[cursor];
                if (escaped) {
                    switch (ch) {
                        case '"': item.push_back('"'); break;
                        case '\\': item.push_back('\\'); break;
                        case '/': item.push_back('/'); break;
                        case 'b': item.push_back('\b'); break;
                        case 'f': item.push_back('\f'); break;
                        case 'n': item.push_back('\n'); break;
                        case 'r': item.push_back('\r'); break;
                        case 't': item.push_back('\t'); break;
                        default: item.push_back(ch); break;
                    }
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    ++cursor;
                    break;
                }
                item.push_back(ch);
            }
            values.push_back(item);
            continue;
        }
        const size_t begin = cursor;
        while (cursor < raw.size() && raw[cursor] != ',') {
            ++cursor;
        }
        const std::string item = trim_copy(raw.substr(begin, cursor - begin));
        if (!item.empty() && item != "null") {
            values.push_back(item);
        }
    }
    if (values.empty()) {
        return std::nullopt;
    }
    std::ostringstream joined;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            joined << list_separator;
        }
        joined << values[i];
    }
    return joined.str();
}

} // namespace mobileslicer::orca_wrapper
