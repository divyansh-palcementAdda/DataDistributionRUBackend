package com.app.datadistribution.service.interfaces;

import jakarta.mail.MessagingException;

public interface IEmailService {
	public void sendOtpEmail(String toEmail, String otp) throws MessagingException;

	public void sendUserUpdateEmail(String email, String string, String message)throws MessagingException;

	public void sendApiCredentials(String email, String apiKey, String rawSecret);
}
