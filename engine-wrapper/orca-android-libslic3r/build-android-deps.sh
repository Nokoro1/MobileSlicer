#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ -n "${ANDROID_NDK_HOME:-}" ]; then
    NDK_ROOT="$ANDROID_NDK_HOME"
elif [ -n "${ANDROID_NDK_ROOT:-}" ]; then
    NDK_ROOT="$ANDROID_NDK_ROOT"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    NDK_ROOT="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
elif [ -d "$PROJECT_ROOT/.android-sdk/ndk" ]; then
    NDK_ROOT="$(find "$PROJECT_ROOT/.android-sdk/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
else
    echo "Android NDK not found. Set ANDROID_NDK_HOME or ANDROID_HOME before running this script." >&2
    exit 2
fi
TOOLCHAIN="$NDK_ROOT/build/cmake/android.toolchain.cmake"
LLVM_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
SYSROOT="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

API_LEVEL="${ANDROID_API_LEVEL:-29}"
ABI="${ANDROID_ABI:-arm64-v8a}"
case "$ABI" in
    arm64-v8a)
        ABI_SUFFIX="arm64"
        HOST_TRIPLE="aarch64-linux-android"
        OPENSSL_TARGET="android-arm64"
        ;;
    x86_64)
        ABI_SUFFIX="x86_64"
        HOST_TRIPLE="x86_64-linux-android"
        OPENSSL_TARGET="android-x86_64"
        ;;
    *)
        echo "Unsupported ANDROID_ABI=$ABI. Supported: arm64-v8a, x86_64." >&2
        exit 2
        ;;
esac
CC="$LLVM_BIN/${HOST_TRIPLE}${API_LEVEL}-clang"
CXX="$LLVM_BIN/${HOST_TRIPLE}${API_LEVEL}-clang++"
AR="$LLVM_BIN/llvm-ar"
RANLIB="$LLVM_BIN/llvm-ranlib"
STRIP="$LLVM_BIN/llvm-strip"

DEPS_BUILD="${ORCA_ANDROID_DEPS_BUILD_ROOT:-$PROJECT_ROOT/engine-wrapper/orca-android-libslic3r/android-deps-build/$ABI_SUFFIX}"
DEPS_INSTALL="${ORCA_ANDROID_DEPS_ROOT:-/tmp/orca-deps-install}"
DEPS_SRC_ROOT="${ORCA_ANDROID_DEPS_SRC_ROOT:-/tmp/orca-deps-src}"

mkdir -p "$DEPS_BUILD" "$DEPS_INSTALL"

NPROC=$(nproc 2>/dev/null || echo 4)

download_extract() {
    local url="$1"
    local dest="$2"
    local strip_components="${3:-1}"

    if [ ! -d "$dest" ]; then
        mkdir -p "$dest"
        cd "$dest"
        curl -fsSL "$url" | tar xz --strip-components="$strip_components"
    fi
}

apply_occt_android_fixes() {
    local src_dir="$1"
    local freetype_file="$src_dir/src/Font/Font_FTFont.cxx"
    local brep_font_file="$src_dir/src/StdPrs/StdPrs_BRepFont.cxx"

    if [ -f "$freetype_file" ] \
            && ! awk 'prev ~ /#ifdef HAVE_FREETYPE/ && /FT_LOAD_TARGET_LIGHT/ { found = 1 } { prev = $0 } END { exit found ? 0 : 1 }' "$freetype_file"; then
        perl -0pi -e 's/(  setLoadFlag \(FT_LOAD_TARGET_LIGHT,   \(theParams\.FontHinting & Font_Hinting_Light\) != 0\);\n  setLoadFlag \(FT_LOAD_NO_HINTING,     \(theParams\.FontHinting & Font_Hinting_Normal\) == 0\n                                    && \(theParams\.FontHinting & Font_Hinting_Light\)  == 0\);\n)/#ifdef HAVE_FREETYPE\n$1#endif\n/' "$freetype_file"
        perl -0pi -e 's/(  setLoadFlag \(FT_LOAD_FORCE_AUTOHINT, \(theParams\.FontHinting & Font_Hinting_ForceAutohint\) != 0\);\n  setLoadFlag \(FT_LOAD_NO_AUTOHINT,    \(theParams\.FontHinting & Font_Hinting_NoAutohint\) != 0\);\n)/#ifdef HAVE_FREETYPE\n$1#endif\n/' "$freetype_file"
    fi

    if [ -f "$brep_font_file" ]; then
        perl -0pi -e 's/const char\* aTags      = &anOutline->tags\[aStartIndex\];/const auto* aTags      = \&anOutline->tags[aStartIndex];/' "$brep_font_file"
    fi
}

# ========== GMP ==========
build_gmp() {
    echo "=== Building GMP ==="
    local src_dir="$DEPS_BUILD/gmp-src"
    local build_dir="$DEPS_BUILD/gmp-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-gmp"

    if [ -f "$install_dir/lib/libgmp.a" ]; then
        echo "GMP already built, skipping"
        return
    fi

    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz | tar xJ --strip-components=1
    fi

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    "$src_dir/configure" \
        --host="$HOST_TRIPLE" \
        --prefix="$install_dir" \
        --enable-shared=no \
        --enable-static=yes \
        --enable-cxx=yes \
        CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
        CFLAGS="-fPIC -DPIC" CXXFLAGS="-fPIC -DPIC"

    make -j"$NPROC"
    make install
    echo "GMP done: $install_dir"
}

# ========== MPFR ==========
build_mpfr() {
    echo "=== Building MPFR ==="
    local src_dir="$DEPS_BUILD/mpfr-src"
    local build_dir="$DEPS_BUILD/mpfr-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-mpfr"
    local gmp_dir="$DEPS_INSTALL/${ABI_SUFFIX}-gmp"

    if [ -f "$install_dir/lib/libmpfr.a" ]; then
        echo "MPFR already built, skipping"
        return
    fi

    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://ftp.gnu.org/gnu/mpfr/mpfr-4.2.1.tar.xz | tar xJ --strip-components=1
    fi

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    "$src_dir/configure" \
        --host="$HOST_TRIPLE" \
        --prefix="$install_dir" \
        --enable-shared=no \
        --enable-static=yes \
        --with-gmp="$gmp_dir" \
        CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
        CFLAGS="-fPIC -DPIC" CXXFLAGS="-fPIC -DPIC"

    make -j"$NPROC"
    make install
    echo "MPFR done: $install_dir"
}

# ========== OpenSSL ==========
build_openssl() {
    echo "=== Building OpenSSL ==="
    local src_dir="$DEPS_BUILD/openssl-src"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-openssl"

    if [ -f "$install_dir/lib/libcrypto.a" ]; then
        echo "OpenSSL already built, skipping"
        return
    fi

    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://github.com/openssl/openssl/releases/download/openssl-3.1.7/openssl-3.1.7.tar.gz | tar xz --strip-components=1
    fi

    cd "$src_dir"
    export ANDROID_NDK_ROOT="$NDK_ROOT"
    export PATH="$LLVM_BIN:$PATH"

    ./Configure "$OPENSSL_TARGET" \
        -D__ANDROID_API__=$API_LEVEL \
        --prefix="$install_dir" \
        no-shared no-tests no-ui-console no-engine

    make -j"$NPROC"
    make install_sw
    echo "OpenSSL done: $install_dir"
}

# ========== Boost ==========
build_boost() {
    echo "=== Building Boost ==="
    local src_dir="$DEPS_BUILD/boost-src"
    local build_dir="$DEPS_BUILD/boost-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-boost-cgal"
    local headers_dir="$DEPS_INSTALL/${ABI_SUFFIX}-boost-headers/include"
    local staged_src_dir="$DEPS_SRC_ROOT/boost-1.84.0"

    if [ -f "$install_dir/lib/libboost_filesystem.a" ] \
            && [ -f "$install_dir/lib/libboost_thread.a" ] \
            && [ -f "$install_dir/lib/libboost_chrono.a" ] \
            && [ -f "$install_dir/lib/libboost_atomic.a" ]; then
        echo "Boost already built, skipping"
        return
    fi

    if [ -d "$src_dir" ] && [ ! -f "$src_dir/CMakeLists.txt" ]; then
        rm -rf "$src_dir"
    fi
    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://github.com/boostorg/boost/releases/download/boost-1.84.0/boost-1.84.0.tar.xz | tar xJ --strip-components=1
    fi
    if [ ! -d "$staged_src_dir" ]; then
        mkdir -p "$(dirname "$staged_src_dir")"
        cp -a "$src_dir" "$staged_src_dir"
    fi

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    cmake "$src_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI=$ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTING=OFF \
        -DBOOST_LOCALE_ENABLE_ICU=OFF \
        -DBOOST_IOSTREAMS_ENABLE_BZIP2=OFF \
        -DBOOST_IOSTREAMS_ENABLE_ZSTD=OFF \
        -DBOOST_IOSTREAMS_ENABLE_LZMA=OFF

    cmake --build . -j"$NPROC" --target boost_filesystem boost_thread boost_iostreams boost_date_time boost_atomic boost_chrono boost_nowide

    # The Boost superproject install step attempts to install every configured
    # component, including optional libraries we deliberately do not build for
    # Android. Stage only the static libraries this wrapper consumes.
    rm -rf "$install_dir"
    mkdir -p "$install_dir/lib"
    cp -a stage/lib/*.a "$install_dir/lib/"

    # Assemble full header tree from source
    echo "Assembling full Boost header tree..."
    rm -rf "$headers_dir"
    mkdir -p "$headers_dir"
    find "$staged_src_dir/libs" -type d -name "include" | while read inc_dir; do
        if [ -d "$inc_dir/boost" ]; then
            cp -rn "$inc_dir/boost" "$headers_dir/" 2>/dev/null || true
        fi
    done
    echo "Boost done: $install_dir (headers: $headers_dir)"
}

# ========== TBB ==========
build_tbb() {
    echo "=== Building TBB ==="
    local src_dir="$DEPS_BUILD/tbb-src"
    local build_dir="$DEPS_BUILD/tbb-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-tbb-static2"

    if [ -f "$install_dir/lib/libtbb.a" ]; then
        echo "TBB already built, skipping"
        return
    fi

    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://github.com/oneapi-src/oneTBB/archive/refs/tags/v2021.11.0.tar.gz | tar xz --strip-components=1
    fi

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    cmake "$src_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI=$ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DBUILD_SHARED_LIBS=OFF \
        -DTBB_TEST=OFF

    cmake --build . -j"$NPROC"
    cmake --install .
    echo "TBB done: $install_dir"
}

# ========== NLopt ==========
build_nlopt() {
    echo "=== Building NLopt ==="
    local src_dir="$DEPS_BUILD/nlopt-src"
    local build_dir="$DEPS_BUILD/nlopt-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-nlopt"

    if [ -f "$install_dir/lib/libnlopt.a" ] && [ -f "$install_dir/include/nlopt.hpp" ]; then
        echo "NLopt already built, skipping"
        return
    fi

    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://github.com/stevengj/nlopt/archive/v2.5.0.tar.gz | tar xz --strip-components=1
    fi

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    cmake "$src_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI=$ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DBUILD_SHARED_LIBS=OFF \
        -DNLOPT_PYTHON=OFF \
        -DNLOPT_OCTAVE=OFF \
        -DNLOPT_MATLAB=OFF \
        -DNLOPT_GUILE=OFF \
        -DNLOPT_SWIG=OFF \
        -DNLOPT_TESTS=OFF

    cmake --build . -j"$NPROC"
    cmake --install .
    echo "NLopt done: $install_dir"
}

# ========== libjpeg-turbo ==========
build_jpeg() {
    echo "=== Building libjpeg-turbo ==="
    local src_dir="$DEPS_BUILD/jpeg-src"
    local build_dir="$DEPS_BUILD/jpeg-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-jpeg"

    if [ -f "$install_dir/lib/libjpeg.a" ] && [ -f "$install_dir/include/jpeglib.h" ]; then
        echo "libjpeg-turbo already built, skipping"
        return
    fi

    download_extract "https://github.com/libjpeg-turbo/libjpeg-turbo/archive/refs/tags/3.0.1.tar.gz" "$src_dir"

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    cmake "$src_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI=$ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DENABLE_SHARED=OFF \
        -DENABLE_STATIC=ON \
        -DWITH_JPEG8=ON \
        -DWITH_SIMD=OFF \
        -DWITH_TURBOJPEG=OFF

    cmake --build . -j"$NPROC"
    cmake --install .
    echo "libjpeg-turbo done: $install_dir"
}

# ========== CGAL (header-only) ==========
build_cgal() {
    echo "=== Building CGAL (header-only) ==="
    local src_dir="$DEPS_BUILD/cgal-src"
    local build_dir="$DEPS_BUILD/cgal-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-cgal"

    if [ -d "$install_dir/include/CGAL" ]; then
        echo "CGAL already installed, skipping"
        return
    fi

    if [ ! -d "$src_dir" ]; then
        mkdir -p "$src_dir"
        cd "$src_dir"
        curl -fsSL https://github.com/CGAL/cgal/releases/download/v5.6.1/CGAL-5.6.1.tar.xz | tar xJ --strip-components=1
    fi

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    cmake "$src_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI=$ABI \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DCGAL_HEADER_ONLY=ON \
        -DBUILD_TESTING=OFF

    cmake --install .
    echo "CGAL done: $install_dir"
}

# ========== OCCT / Open CASCADE (STEP import) ==========
build_occt() {
    echo "=== Building OCCT (STEP import) ==="
    local src_dir="$DEPS_BUILD/occt-src"
    local build_dir="$DEPS_BUILD/occt-build"
    local install_dir="$DEPS_INSTALL/${ABI_SUFFIX}-occt"
    local patch_file="$PROJECT_ROOT/vendor/orcaslicer/deps/OCCT/0001-OCCT-fix.patch"

    if [ -f "$install_dir/lib/cmake/occt/OpenCASCADEConfig.cmake" ] \
            && [ -f "$install_dir/lib/occt/libTKXDESTEP.a" ]; then
        echo "OCCT already built, skipping"
        return
    fi

    download_extract "https://github.com/Open-Cascade-SAS/OCCT/archive/refs/tags/V7_6_0.tar.gz" "$src_dir"

    if [ -f "$patch_file" ] && [ ! -f "$src_dir/.mobileslicer-orca-occt-patch-applied" ]; then
        cd "$src_dir"
        git apply --verbose --ignore-space-change --whitespace=fix "$patch_file"
        touch "$src_dir/.mobileslicer-orca-occt-patch-applied"
    fi
    apply_occt_android_fixes "$src_dir"

    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    cmake "$src_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$API_LEVEL" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_CXX_STANDARD=17 \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DINSTALL_DIR_LAYOUT=Unix \
        -DINSTALL_DIR_BIN=bin/occt \
        -DINSTALL_DIR_LIB=lib/occt \
        -DINSTALL_DIR_INCLUDE=include/occt \
        -DINSTALL_DIR_CMAKE=lib/cmake/occt \
        -DBUILD_LIBRARY_TYPE=Static \
        -DBUILD_DOC_Overview=OFF \
        -DBUILD_MODULE_ApplicationFramework=OFF \
        -DBUILD_MODULE_Draw=OFF \
        -DBUILD_MODULE_FoundationClasses=OFF \
        -DBUILD_MODULE_ModelingAlgorithms=OFF \
        -DBUILD_MODULE_ModelingData=OFF \
        -DBUILD_MODULE_Visualization=OFF \
        -DUSE_FFMPEG=OFF \
        -DUSE_FREEIMAGE=OFF \
        -DUSE_FREETYPE=OFF \
        -DUSE_OPENGL=OFF \
        -DUSE_RAPIDJSON=OFF \
        -DUSE_TBB=OFF \
        -DUSE_TCL=OFF \
        -DUSE_TK=OFF \
        -DUSE_VTK=OFF

    cmake --build . -j"$NPROC"
    cmake --install .

    mkdir -p "$install_dir/share/licenses/occt"
    cp "$src_dir/LICENSE_LGPL_21.txt" "$install_dir/share/licenses/occt/" 2>/dev/null || true
    cp "$src_dir/OCCT_LGPL_EXCEPTION.txt" "$install_dir/share/licenses/occt/" 2>/dev/null || true

    echo "OCCT done: $install_dir"
}

echo "Building Android dependencies..."
echo "NDK: $NDK_ROOT"
echo "ABI: $ABI ($ABI_SUFFIX)"
echo "Build dir: $DEPS_BUILD"
echo "Install dir: $DEPS_INSTALL"
echo "Source stage dir: $DEPS_SRC_ROOT"
echo ""

build_gmp
build_mpfr
build_openssl
build_boost
build_tbb
build_nlopt
build_jpeg
build_cgal
build_occt

echo ""
echo "=== All dependencies built ==="
echo "GMP:     $DEPS_INSTALL/${ABI_SUFFIX}-gmp"
echo "MPFR:    $DEPS_INSTALL/${ABI_SUFFIX}-mpfr"
echo "OpenSSL: $DEPS_INSTALL/${ABI_SUFFIX}-openssl"
echo "Boost:   $DEPS_INSTALL/${ABI_SUFFIX}-boost-cgal (headers: $DEPS_INSTALL/${ABI_SUFFIX}-boost-headers/include)"
echo "TBB:     $DEPS_INSTALL/${ABI_SUFFIX}-tbb-static2"
echo "NLopt:   $DEPS_INSTALL/${ABI_SUFFIX}-nlopt"
echo "JPEG:    $DEPS_INSTALL/${ABI_SUFFIX}-jpeg"
echo "CGAL:    $DEPS_INSTALL/${ABI_SUFFIX}-cgal"
echo "OCCT:    $DEPS_INSTALL/${ABI_SUFFIX}-occt"
echo "Boost source: $DEPS_SRC_ROOT/boost-1.84.0"
