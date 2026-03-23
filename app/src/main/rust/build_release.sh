#!/bin/bash

cargo install cargo-ndk
rustup update
rustup override set nightly
rustup target add x86_64-linux-android
rustup target add armv7-linux-androideabi
rustup target add aarch64-linux-android
#rustup update

cargo ndk -t x86_64 -t armeabi-v7a -t arm64-v8a -o ../jniLibs build --release

