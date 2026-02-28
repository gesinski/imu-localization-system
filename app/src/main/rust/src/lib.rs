use jni::objects::JClass;
use jni::JNIEnv;
//<RustJNI>
// primitive imports
use jni::sys::jstring;
//</RustJNI>

// cd ~/imu-localization-system/app/src/main/rust/
// cargo ndk -t x86_64 -t armeabi-v7a -t arm64-v8a -o ../jniLibs build --release
        
#[no_mangle]
pub extern "C" fn Java_com_inertial_navigation_MainActivity_calculateIMU(
    env: JNIEnv,
    _class: JClass,
    
) -> jstring {
    println!("Parameters: {:?}", ());

    let output = r#"Rust Method: udalo sie"#;
env.new_string(output)
    .expect("Couldn't create Java string!")
    .into_raw()
}