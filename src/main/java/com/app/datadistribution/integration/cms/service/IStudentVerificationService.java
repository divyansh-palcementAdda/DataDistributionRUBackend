package com.app.datadistribution.integration.cms.service;

import com.app.datadistribution.integration.cms.dto.StudentVerificationRequest;
import com.app.datadistribution.integration.cms.dto.StudentVerificationResponse;

public interface IStudentVerificationService {
    StudentVerificationResponse verifyStudent(StudentVerificationRequest request);
}
