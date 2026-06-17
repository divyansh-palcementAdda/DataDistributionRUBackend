package com.app.datadistribution.service.impl;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OtpService {

    private final Map<String, OtpData> otpMap = new ConcurrentHashMap<>();
    private final int OTP_EXPIRY_MINUTES = 5;

    @Data
    @AllArgsConstructor
    private static class OtpData {
        private String otp;
        private LocalDateTime expiry;
    }

    public String generateOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpMap.put(email, new OtpData(otp, LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)));
        log.info("Generated OTP for {}: {}", email, otp);
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        OtpData data = otpMap.get(email);
        if (data == null) {
            log.warn("No OTP found for {}", email);
            return false;
        }

        if (data.getExpiry().isBefore(LocalDateTime.now())) {
            log.warn("OTP expired for {}", email);
            otpMap.remove(email);
            return false;
        }

        boolean isValid = data.getOtp().equals(otp);
        if (isValid) {
            log.info("OTP verified successfully for {}", email);
            // OTP should probably only be removed after successful user creation, 
            // but for simple verification it's okay to keep it until the next step.
        } else {
            log.warn("Invalid OTP attempt for {}: {}", email, otp);
        }
        return isValid;
    }

    public void clearOtp(String email) {
        otpMap.remove(email);
    }
}
