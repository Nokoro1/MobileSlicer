#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NDK_ROOT="$PROJECT_ROOT/.android-sdk/ndk/26.1.10909125"
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

    if [ -f "$install_dir/lib/libboost_filesystem.a" ]; then
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
build_cgal

echo ""
echo "=== All dependencies built ==="
echo "GMP:     $DEPS_INSTALL/${ABI_SUFFIX}-gmp"
echo "MPFR:    $DEPS_INSTALL/${ABI_SUFFIX}-mpfr"
echo "OpenSSL: $DEPS_INSTALL/${ABI_SUFFIX}-openssl"
echo "Boost:   $DEPS_INSTALL/${ABI_SUFFIX}-boost-cgal (headers: $DEPS_INSTALL/${ABI_SUFFIX}-boost-headers/include)"
echo "TBB:     $DEPS_INSTALL/${ABI_SUFFIX}-tbb-static2"
echo "NLopt:   $DEPS_INSTALL/${ABI_SUFFIX}-nlopt"
echo "CGAL:    $DEPS_INSTALL/${ABI_SUFFIX}-cgal"
echo "Boost source: $DEPS_SRC_ROOT/boost-1.84.0"
