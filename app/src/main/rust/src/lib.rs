use jni::JNIEnv;
use jni::objects::JObject;
use jni::sys::jfloatArray;
use std::sync::{Mutex, LazyLock};

// ex. cd ~/imu-localization-system/app/src/main/rust/
// cargo ndk -t x86_64 -t armeabi-v7a -t arm64-v8a -o ../jniLibs build --release

static FILTER: LazyLock<Mutex<ImuFilter>> =
    LazyLock::new(|| Mutex::new(ImuFilter::new(180.0)));

#[no_mangle]
pub extern "C" fn Java_com_inertial_navigation_logic_NativeLib_updateIMU(
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
        mag:[f32;3],
        dt:f32
    ) -> (bool, f32) {
        let step_info = self.detect_step(acc);


        let v_a = Normalize::v3(acc);
        let v_m = Normalize::v3(mag);
        let [qw, qx, qy, qz] = self.q;

        // Gravity Vector Estimation 
        // calculate where the gravity vector 'v' should be, based on our 
        // current knowledge of orientation (the quaternion). 
        // This represents the 3rd row of the rotation matrix.
        let vx = 2.0 * (qx*qz - qw*qy);
        let vy = 2.0 * (qw*qx + qy*qz);
        let vz = qw*qw - qx*qx - qy*qy + qz*qz;

        // magnetic field estimation and reference alignment
        // rotate the raw magnetic measurement into the Earth frame 
        // to compensate for the current device tilt.
        let hx = v_m[0] * (qw*qw + qx*qx - qy*qy - qz*qz) + v_m[1] * (2.0 * (qx*qy - qw*qz)) + v_m[2] * (2.0 * (qx*qz + qw*qy));
        let hy = v_m[0] * (2.0 * (qx*qy + qw*qz)) + v_m[1] * (qw*qw - qx*qx + qy*qy - qz*qz) + v_m[2] * (2.0 * (qy*qz - qw*qx));
        // calculate the magnitude in the horizontal plane (bx) and 
        // ignore 'hy' by aligning our reference 'b' to the magnetic North.
        let bx = (hx*hx + hy*hy).sqrt();
        let bz = v_m[0] * (2.0 * (qx*qz - qw*qy)) + v_m[1] * (2.0 * (qy*qz + qw*qx)) + v_m[2] * (qw*qw - qx*qx - qy*qy + qz*qz);

        // rotate the reference 'b' back into the Body frame to get 
        // the estimated magnetic vector 'w'.
        let wx = bx * (qw*qw + qx*qx - qy*qy - qz*qz) + bz * (2.0 * (qx*qz - qw*qy));
        let wy = bx * (2.0 * (qx*qy - qw*qz)) + bz * (2.0 * (qy*qz + qw*qx));
        let wz = bx * (2.0 * (qx*qz + qw*qy)) + bz * (qw*qw - qx*qx - qy*qy + qz*qz);

        // error calculation using Vector Cross Product
        // find the angular difference (error) between measured sensors (v_a, v_m) 
        // and estimated vectors (v, w). The cross product gives an axis 
        // proportional to the sine of the angle between them.
        let ex = (v_a[1]*vz - v_a[2]*vy) + (v_m[1]*wz - v_m[2]*wy);
        let ey = (v_a[2]*vx - v_a[0]*vz) + (v_m[2]*wx - v_m[0]*wz);
        let ez = (v_a[0]*vy - v_a[1]*vx) + (v_m[0]*wy - v_m[1]*wx);

        // kp (Proportional Gain) dictates how fast the quaternion "drifts" 
        // towards the sensor measurements.
        let kp = 0.5;

        // correcting gyroscope 
        // add the error to the raw gyroscope rates. This "compensates" for 
        // gyro bias and drift before the integration step.
        let gx = gyro[0] + kp*ex;
        let gy = gyro[1] + kp*ey;
        let gz = gyro[2] + kp*ez;

        // quaternion time derivative and integration
        // update the orientation by integrating the corrected angular velocity.
        // formula is: q_dot = 0.5 * q * omega
        self.q[0] += 0.5 * (-qx*gx - qy*gy - qz*gz) * dt;
        self.q[1] += 0.5 * ( qw*gx + qy*gz - qz*gy) * dt;
        self.q[2] += 0.5 * ( qw*gy - qx*gz + qz*gx) * dt;
        self.q[3] += 0.5 * ( qw*gz + qx*gy - qy*gx) * dt;

        // Re-normalization quaternion
        self.q = Normalize::v4(self.q);

        step_info
    }

    fn heading(
        &self,
    ) -> f32 {
    // conversion from quaternion to euler angle (yaw/heading).
    // this extracts the rotation around the Earth's vertical (Z) axis.
    // result is in radians, where 0 is north.
        let q = self.q;
        (2.0*(q[0]*q[3] + q[1]*q[2])).atan2(1.0 - 2.0*(q[2]*q[2] + q[3]*q[3]))
    }

    
}

struct Normalize;

impl Normalize {
    fn v3( vec:[f32;3] ) -> [f32;3] {
        let length = (vec[0].powi(2) + vec[1].powi(2) + vec[2].powi(2)).sqrt();
        
        [vec[0] / length, vec[1] / length, vec[2] / length]
    }

    fn v4( vec:[f32;4] ) -> [f32;4] {
        let length = (vec[0].powi(2) + vec[1].powi(2) + vec[2].powi(2) + vec[3].powi(2)).sqrt();
        
        [vec[0] / length, vec[1] / length, vec[2] / length, vec[3] / length]
    }
}