use jni::JNIEnv;
use jni::objects::JObject;
use jni::sys::jfloatArray;
use std::sync::{Mutex, LazyLock};

// ex. cd ~/imu-localization-system/app/src/main/rust/
// cargo ndk -t x86_64 -t armeabi-v7a -t arm64-v8a -o ../jniLibs build --release

static FILTER: LazyLock<Mutex<ImuFilter>> =
    LazyLock::new(|| Mutex::new(ImuFilter::new(180.0)));

#[no_mangle]
pub extern "C" fn Java_com_inertial_navigation_MainActivity_updateIMU(
    env: JNIEnv,
    _obj: JObject,
    gx: f32, gy: f32, gz: f32,
    ax: f32, ay: f32, az: f32,
    mx: f32, my: f32, mz: f32,
    dt: f32
) -> jfloatArray {

    let mut filter = FILTER.lock().unwrap();

    let (is_step, step_length) = filter.update(
        [gx, gy, gz],
        [ax, ay, az],
        [mx, my, mz],
        dt
    );

    let h = filter.heading();

    let step_flag = if is_step { 1.0 } else { 0.0 };
    let output = [h, step_flag, step_length];

    let j_array = env.new_float_array(3).unwrap();
    env.set_float_array_region(&j_array, 0, &output).unwrap();
    j_array.into_raw()
}

struct ImuFilter {
    q: [f32;4],
    previous_norm_acc: f32,
    steps: u32,
    alpha: f32,

    current_max_acc: f32,
    current_min_acc: f32,
    k_factor: f32,

    
}

impl ImuFilter {

    fn new(height_in_cm: f32) -> Self {
        Self {
            q: [1.0, 0.0, 0.0, 0.0],
            previous_norm_acc: 9.81,
            steps: 0,
            alpha: 0.15,  
            current_max_acc: 0.0,
            current_min_acc: 20.0, 
            k_factor: height_in_cm * 0.0025,
        }
    }

    // calculate step length
    fn step_length(&mut self) -> f32 {
        // Model Weinberga: L = K * root4(acc_max - acc_min)
        let length = self.k_factor * (self.current_max_acc - self.current_min_acc).powf(0.25);

        self.current_max_acc = 0.0;
        self.current_min_acc = 20.0;
        
        length
    }


    // step detection
    fn detect_step(&mut self, acc: [f32;3]) -> (bool, f32) {
        let raw_norm = (acc[0]*acc[0] + acc[1]*acc[1] + acc[2]*acc[2]).sqrt();
        let filtered_norm = self.alpha * raw_norm + (1.0 - self.alpha) * self.previous_norm_acc;
        let threshold = 12.0; 
        // temporary hardcoded 
        // TODO: dynamic threshold calculatio ex: Low-pass filter | Adaptive Threshold
        
        if filtered_norm > self.current_max_acc { 
            self.current_max_acc = filtered_norm; 
        }
        if filtered_norm < self.current_min_acc { 
            self.current_min_acc = filtered_norm; 
        }

        let is_step = filtered_norm > threshold && self.previous_norm_acc <= threshold;
        self.previous_norm_acc = filtered_norm;
        
        if is_step { 
            self.steps += 1; 
            (true, self.step_length())
        } else {
            (false, 0.0)
        }
        
    }

    fn update(
        &mut self,
        gyro:[f32;3],
        acc:[f32;3],
        _mag:[f32;3],
        dt:f32
    ) -> (bool, f32) {
        let step_info = self.detect_step(acc);


        // simple gyro integration (temporary)

        let gx = gyro[0];
        let gy = gyro[1];
        let gz = gyro[2];

        let [qw, qx, qy, qz] = self.q;

        self.q[0] += 0.5 * (-qx*gx - qy*gy - qz*gz) * dt;
        self.q[1] += 0.5 * ( qw*gx + qy*gz - qz*gy) * dt;
        self.q[2] += 0.5 * ( qw*gy - qx*gz + qz*gx) * dt;
        self.q[3] += 0.5 * ( qw*gz + qx*gy - qy*gx) * dt;

        // normalize quaternion
        let norm = (self.q[0]*self.q[0] + self.q[1]*self.q[1] + self.q[2]*self.q[2] + self.q[3]*self.q[3]).sqrt();

        self.q[0] /= norm;
        self.q[1] /= norm;
        self.q[2] /= norm;
        self.q[3] /= norm;

        step_info
    }

    fn heading(&self) -> f32 {

        let q = self.q;

        (2.0*(q[0]*q[3] + q[1]*q[2]))
            .atan2(1.0 - 2.0*(q[2]*q[2] + q[3]*q[3]))
    }
}