use jni::JNIEnv;
use jni::objects::JObject;
use std::sync::{Mutex, LazyLock};

// ex. cd ~/imu-localization-system/app/src/main/rust/
// cargo ndk -t x86_64 -t armeabi-v7a -t arm64-v8a -o ../jniLibs build --release

static FILTER: LazyLock<Mutex<ImuFilter>> =
    LazyLock::new(|| Mutex::new(ImuFilter::new()));

#[no_mangle]
pub extern "C" fn Java_com_inertial_navigation_MainActivity_updateIMU(
    _env: JNIEnv,
    _obj: JObject,
    gx: f32, gy: f32, gz: f32,
    ax: f32, ay: f32, az: f32,
    mx: f32, my: f32, mz: f32,
    dt: f32
) -> f32 {

    let mut filter = FILTER.lock().unwrap();

    filter.update(
        [gx, gy, gz],
        [ax, ay, az],
        [mx, my, mz],
        dt
    );

    filter.heading()
}

struct ImuFilter {
    q: [f32;4],
}

impl ImuFilter {

    fn new() -> Self {
        Self {
            q: [1.0, 0.0, 0.0, 0.0],
        }
    }

    fn update(
        &mut self,
        gyro:[f32;3],
        _acc:[f32;3],
        _mag:[f32;3],
        dt:f32
    ) {

        // simple gyro integration (temporary)

        let gx = gyro[0];
        let gy = gyro[1];
        let gz = gyro[2];

        let q = &mut self.q;

        let qw = q[0];
        let qx = q[1];
        let qy = q[2];
        let qz = q[3];

        q[0] += 0.5 * (-qx*gx - qy*gy - qz*gz) * dt;
        q[1] += 0.5 * ( qw*gx + qy*gz - qz*gy) * dt;
        q[2] += 0.5 * ( qw*gy - qx*gz + qz*gx) * dt;
        q[3] += 0.5 * ( qw*gz + qx*gy - qy*gx) * dt;

        // normalize quaternion
        let norm = (q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]).sqrt();

        q[0] /= norm;
        q[1] /= norm;
        q[2] /= norm;
        q[3] /= norm;
    }

    fn heading(&self) -> f32 {

        let q = self.q;

        (2.0*(q[0]*q[3] + q[1]*q[2]))
            .atan2(1.0 - 2.0*(q[2]*q[2] + q[3]*q[3]))
    }
}