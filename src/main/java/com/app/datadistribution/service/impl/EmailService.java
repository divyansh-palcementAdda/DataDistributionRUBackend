package com.app.datadistribution.service.impl;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.app.datadistribution.service.interfaces.IEmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService implements IEmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final Executor emailExecutor;

    private final String fromAddress = "chancelloroffice@renaissance.ac.in";
    private final String fromName = "Renaissance University - Chancellor Office";

    public EmailService(JavaMailSender mailSender, @Qualifier("taskExecutor") Executor emailExecutor) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender required");
        this.emailExecutor = Objects.requireNonNull(emailExecutor, "emailExecutor required");
    }

    @Override
    public void sendOtpEmail(String toEmail, String otp) throws MessagingException {
        validateEmailParams(toEmail, otp);
        String subject = "🔐 Your Secure OTP Code from RCEF";
        String body = buildOtpHtml(otp);

        emailExecutor.execute(() -> {
            try {
                sendHtmlEmailWithInlineLogo(toEmail, subject, body);
                log.info("OTP email sent to {}", toEmail);
            } catch (Exception e) {
                log.error("Failed to send OTP email to {} : {}", toEmail, e.getMessage(), e);
            }
        });
    }

    // ----------------------- FIXED METHOD -----------------------

    @Override
    public void sendUserUpdateEmail(String email, String subject, String message) {

        validateEmailParams(email, message);

        emailExecutor.execute(() -> {
            try {
                sendHtmlEmailWithInlineLogo(email, subject, buildSimpleHtml(message));
                log.info("User update email sent to {}", email);
            } catch (Exception e) {
                log.error("Failed to send user update email to {} : {}", email, e.getMessage(), e);
            }
        });
    }

    // ----------------------- FIXED METHOD -----------------------

    @Override
    public void sendApiCredentials(String email, String apiKey, String rawSecret) {

        validateEmailParams(email, apiKey);

        String subject = "Your API Access Credentials – Action Required";

        String body = buildApiCredentialsHtml(apiKey, rawSecret);

        emailExecutor.execute(() -> {
            try {
                sendHtmlEmailWithInlineLogo(email, subject, body);
                log.info("API credentials email sent to {}", email);
            } catch (Exception e) {
                log.error("Failed to send API credentials email to {} : {}", email, e.getMessage(), e);
            }
        });
    }

    private String buildApiCredentialsHtml(String apiKey, String rawSecret) {
        int year = java.time.LocalDate.now().getYear();
        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="en" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <meta name="x-apple-disable-message-reformatting">
                            <title>API Access Granted</title>
                            <!--[if mso]>
                            <xml>
                                <o:OfficeDocumentSettings>
                                    <o:PixelsPerInch>96</o:PixelsPerInch>
                                </o:OfficeDocumentSettings>
                            </xml>
                            <![endif]-->
                            <style>
                                body { margin: 0; padding: 0; width: 100%% !important; -webkit-text-size-adjust: 100%%; -ms-text-size-adjust: 100%%; background-color: #f4f7ff; font-family: 'Inter', Segoe UI, Roboto, sans-serif; }
                                img { border: 0; height: auto; line-height: 100%%; outline: none; text-decoration: none; }
                                table { border-collapse: collapse !important; }
                                .container { width: 100%% !important; max-width: 600px !important; margin: 0 auto; background-color: #ffffff; }
                                .hero-gradient { background: #6366f1; background: linear-gradient(135deg, #6366f1 0%%, #4f46e5 100%%); }
                                .code-block { background-color: #111827; color: #e5e7eb; border-radius: 8px; font-family: 'Courier New', Courier, monospace; font-size: 13px; line-height: 1.6; }
                                .json-key { color: #818cf8; }
                                .json-string { color: #34d399; }
                                .json-num { color: #f87171; }
                                .badge { background-color: #eef2ff; color: #4f46e5; border-radius: 20px; font-size: 11px; font-weight: bold; padding: 4px 10px; display: inline-block; }
                                .alert-box { border-radius: 12px; font-size: 13px; line-height: 1.5; margin-bottom: 20px; }
                                .credential-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
                                @media only screen and (max-width: 600px) {
                                    .mob-stack { display: block !important; width: 100%% !important; padding-right: 0 !important; padding-left: 0 !important; }
                                }
                            </style>
                        </head>
                        <body style="margin:0;padding:0;">

                            <table width="100%%" border="0" cellspacing="0" cellpadding="0" bgcolor="#f4f7ff">
                                <tr>
                                    <td align="center">
                                        <table width="600" class="container" border="0" cellspacing="0" cellpadding="0" bgcolor="#ffffff" style="max-width:600px;">
                                            <tr>
                                                <td class="hero-gradient" style="padding: 20px 25px; border-bottom: 1px solid rgba(255,255,255,0.1);">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                        <tr>
                                                            <td><img src="cid:logoImage" alt="AreYouReporting" style="display:block; height:32px; filter: brightness(0) invert(1);"></td>
                                                            <td align="right" style="color:#ffffff; font-size:12px; font-weight:600; letter-spacing:1px;">CONSOLE ACCESS</td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td class="hero-gradient" align="center" style="padding: 50px 25px 60px 25px; color:#ffffff;">
                                                    <table width="80" height="80" border="0" cellspacing="0" cellpadding="0" bgcolor="rgba(255,255,255,0.15)" style="border-radius:50%%;">
                                                        <tr><td align="center" valign="middle" style="color:#ffffff; font-size:40px; font-weight:bold;">✓</td></tr>
                                                    </table>
                                                    <h1 style="margin:25px 0 10px 0; font-size:32px; font-weight:800; letter-spacing:-0.5px;">API Access Granted</h1>
                                                    <p style="margin:0; font-size:16px; line-height:1.6; opacity:0.9;">You now have full access to our Student Admission API. Seamlessly integrate and start building the future of reporting today.</p>
                                                </td>
                                            </tr>
                                            <!-- Credentials Section -->
                                            <tr>
                                                <td style="padding: 30px 25px 0 25px;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="border:1px solid #e5e7eb; border-radius:16px; overflow:hidden;">
                                                        <tr>
                                                            <td bgcolor="#f8fafc" style="padding:15px 20px; border-bottom:1px solid #e5e7eb;">
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                                    <tr>
                                                                        <td style="font-size:14px; font-weight:800; color:#475569; text-transform:uppercase; letter-spacing:0.5px;">🔑 Your API Credentials</td>
                                                                        <td align="right"><span style="background-color:#fef2f2; color:#ef4444; font-size:10px; font-weight:800; padding:4px 8px; border-radius:4px; text-transform:uppercase;">Confidential</span></td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td style="padding: 25px;">
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                                    <tr>
                                                                        <td style="padding-bottom:20px;">
                                                                            <div style="font-size:11px; font-weight:700; color:#94a3b8; margin-bottom:8px; text-transform:uppercase;">Client ID / API Key</div>
                                                                            <div style="background-color:#f1f5f9; padding:15px; border-radius:10px; font-family:monospace; font-size:14px; color:#1e293b; border:1px solid #e2e8f0; word-break:break-all;">%1$s</div>
                                                                        </td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td>
                                                                            <div style="font-size:11px; font-weight:700; color:#94a3b8; margin-bottom:8px; text-transform:uppercase;">Client Secret</div>
                                                                            <div style="background-color:#f1f5f9; padding:15px; border-radius:10px; font-family:monospace; font-size:14px; color:#1e293b; border:1px solid #e2e8f0; word-break:break-all;">%2$s</div>
                                                                        </td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 30px 25px;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color:#f9fafb; border:1px solid #e5e7eb; border-radius:16px; padding:25px;">
                                                        <tr><td style="padding-bottom:20px; font-size:16px; font-weight:700; color:#111827;">📊 Integration Journey</td></tr>
                                                        <tr>
                                                            <td>
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                                    <tr>
                                                                        <td width="25%%" align="center" class="mob-stack" style="padding:10px 0;">
                                                                            <table width="32" height="32" border="0" cellspacing="0" cellpadding="0" bgcolor="#6366f1" style="border-radius:50%%; margin-bottom:8px;"><tr><td align="center" style="color:#ffffff; font-weight:bold; font-size:13px;">1</td></tr></table>
                                                                            <div style="font-size:12px; font-weight:700; color:#111827;">Registration</div>
                                                                            <div style="font-size:10px; color:#6b7280;">User details</div>
                                                                        </td>
                                                                        <td width="25%%" align="center" class="mob-stack" style="padding:10px 0;">
                                                                            <table width="32" height="32" border="0" cellspacing="0" cellpadding="0" bgcolor="#ec4899" style="border-radius:50%%; margin-bottom:8px;"><tr><td align="center" style="color:#ffffff; font-weight:bold; font-size:13px;">2</td></tr></table>
                                                                            <div style="font-size:12px; font-weight:700; color:#111827;">Payment</div>
                                                                            <div style="font-size:10px; color:#6b7280;">Verify completion</div>
                                                                        </td>
                                                                        <td width="25%%" align="center" class="mob-stack" style="padding:10px 0;">
                                                                            <table width="32" height="32" border="0" cellspacing="0" cellpadding="0" bgcolor="#3b82f6" style="border-radius:50%%; margin-bottom:8px;"><tr><td align="center" style="color:#ffffff; font-weight:bold; font-size:13px;">3</td></tr></table>
                                                                            <div style="font-size:12px; font-weight:700; color:#111827;">Token</div>
                                                                            <div style="font-size:10px; color:#6b7280;">Get credentials</div>
                                                                        </td>
                                                                        <td width="25%%" align="center" class="mob-stack" style="padding:10px 0;">
                                                                            <table width="32" height="32" border="0" cellspacing="0" cellpadding="0" bgcolor="#9ca3af" style="border-radius:50%%; margin-bottom:8px;"><tr><td align="center" style="color:#ffffff; font-weight:bold; font-size:13px;">4</td></tr></table>
                                                                            <div style="font-size:12px; font-weight:700; color:#111827;">API Call</div>
                                                                            <div style="font-size:10px; color:#6b7280;">Submit admission</div>
                                                                        </td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 0 25px 30px 25px;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="border:1px solid #e5e7eb; border-radius:16px; padding:25px;">
                                                        <tr>
                                                            <td style="padding-bottom:15px;">
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                                    <tr>
                                                                        <td style="font-size:18px; font-weight:800; color:#111827;">Generate Access Token</td>
                                                                        <td align="right"><span class="badge">POST https://cms.areyoureporting.com/api/auth/api-clients/token</span></td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                        <tr><td style="padding-bottom:20px; font-size:14px; color:#6b7280;">Acquire your Bearer token to authorize subsequent requests.</td></tr>
                                                        <tr>
                                                            <td>
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                                    <tr>
                                                                        <td width="48%%" valign="top" class="mob-stack" style="padding-right:10px;">
                                                                            <div style="font-size:10px; color:#9ca3af; font-weight:bold; margin-bottom:8px; text-transform:uppercase;">REQUEST BODY</div>
                                                                            <table width="100%%" border="0" cellspacing="0" cellpadding="0" class="code-block" style="padding:15px;">
                                                                                <tr><td>{<br>&nbsp;&nbsp;<span class="json-key">"apiKey"</span>: <span class="json-string">"%1$s"</span>,<br>&nbsp;&nbsp;<span class="json-key">"apiSecret"</span>: <span class="json-string">"%2$s"</span>}</td></tr>
                                                                            </table>
                                                                        </td>
                                                                        <td width="4%%" class="mob-stack" style="font-size:1px;">&nbsp;</td>
                                                                        <td width="48%%" valign="top" class="mob-stack">
                                                                            <div style="font-size:10px; color:#9ca3af; font-weight:bold; margin-bottom:8px; text-transform:uppercase;">RESPONSE <span style="float:right; color:#10b981;">● 200 OK</span></div>
                                                                            <table width="100%%" border="0" cellspacing="0" cellpadding="0" class="code-block" style="padding:15px;">
                                                                                <tr><td>{<br>&nbsp;&nbsp;<span class="json-key">"access_token"</span>: <span class="json-string">"eyJh..."</span>,<br>&nbsp;&nbsp;<span class="json-key">"token_type"</span>: <span class="json-string">"Bearer"</span>,<br>&nbsp;&nbsp;<span class="json-key">"expires_in"</span>: <span class="json-num">3600</span><br>}</td></tr>
                                                                            </table>
                                                                        </td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 0 25px 30px 25px;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="border:1px solid #e5e7eb; border-radius:16px; padding:25px;">
                                                        <tr>
                                                            <td style="padding-bottom:15px;">
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                                    <tr>
                                                                        <td style="font-size:18px; font-weight:800; color:#111827;">Create Student Admission</td>
                                                                        <td align="right"><span class="badge">POST https://cms.areyoureporting.com/api/students/website-admission</span></td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                        <tr><td style="padding-bottom:20px; font-size:14px; color:#6b7280;">Submit new student applications directly to our system.</td></tr>
                                                        <tr>
                                                            <td style="padding-bottom:15px;">
                                                                <div style="font-size:10px; color:#9ca3af; font-weight:bold; margin-bottom:8px; text-transform:uppercase;">HEADERS</div>
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:12px; font-family:monospace; font-size:12px;">
                                                                    <tr><td><span style="color:#4f46e5; font-weight:bold;">Authorization:</span> Bearer &lt;YOUR_TOKEN&gt; &nbsp;&nbsp; | &nbsp;&nbsp; <span style="color:#4f46e5; font-weight:bold;">Content-Type:</span> application/json</td></tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <div style="font-size:10px; color:#9ca3af; font-weight:bold; margin-bottom:8px; text-transform:uppercase;">REQUEST PAYLOAD VIEW</div>
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0" class="code-block" style="padding:20px; border-radius:12px;">
                                                                    <tr><td style="font-size:12px;">{<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"sourceUniqueId"</span>: <span class="json-string">"WEB-REG-20260406-001"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"fullName"</span>: <span class="json-string">"John Doe"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"dob"</span>: <span class="json-string">"2002-08-15"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"gender"</span>: <span class="json-string">"Male"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"email"</span>: <span class="json-string">"john.doe@example.com"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"contactNumber"</span>: <span class="json-string">"9876543210"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"whatsappNumber"</span>: <span class="json-string">"9876543210"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"fatherName"</span>: <span class="json-string">"Robert Doe"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"motherName"</span>: <span class="json-string">"Jane Doe"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"parentsContactNumber"</span>: <span class="json-string">"9123456780"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"counselledBy"</span>: <span class="json-string">"Amit Sharma"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"session"</span>: <span class="json-num">2026</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"programName"</span>: <span class="json-string">"School Of Management"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"courseName"</span>: <span class="json-string">"MBA"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"state"</span>: <span class="json-string">"Madhya Pradesh"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"city"</span>: <span class="json-string">"Bhopal"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"pincode"</span>: <span class="json-string">"462001"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"amountPaid"</span>: <span class="json-num">15000.0</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"paymentMethod"</span>: <span class="json-string">"UPI"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"razorpayPaymentId"</span>: <span class="json-string">"pay_ABC123XYZ"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"paymentDate"</span>: <span class="json-string">"2026-04-06T10:15:30"</span>,<br>
                                                                    &nbsp;&nbsp;<span class="json-key">"completedAt"</span>: <span class="json-string">"2026-04-06T10:20:45"</span><br>
                                                                    }</td></tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 0 25px 30px 25px;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                        <tr>
                                                            <td width="48%%" valign="top" class="mob-stack" style="padding-right:10px;">
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0" bgcolor="#fef2f2" class="alert-box" style="border:1px solid #fee2e2; padding:15px;">
                                                                    <tr>
                                                                        <td width="30" valign="top"><table width="20" height="20" border="0" cellspacing="0" cellpadding="0" bgcolor="#ef4444" style="border-radius:50%%;"><tr><td align="center" style="color:#ffffff; font-size:12px; font-weight:bold;">!</td></tr></table></td>
                                                                        <td style="color:#991b1b;"><strong style="display:block; margin-bottom:5px;">Do Not Call Before Payment</strong>Admission API units should only happen <span style="color:#dc2626; font-weight:bold;">after payment success</span>.</td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                            <td width="4%%" class="mob-stack" style="font-size:1px;">&nbsp;</td>
                                                            <td width="48%%" valign="top" class="mob-stack">
                                                                <table width="100%%" border="0" cellspacing="0" cellpadding="0" bgcolor="#f0fdf4" class="alert-box" style="border:1px solid #dcfce7; padding:15px;">
                                                                    <tr>
                                                                        <td width="30" valign="top"><table width="20" height="20" border="0" cellspacing="0" cellpadding="0" bgcolor="#10b981" style="border-radius:50%%;"><tr><td align="center" style="color:#ffffff; font-size:12px; font-weight:bold;">✓</td></tr></table></td>
                                                                        <td style="color:#166534;"><strong style="display:block; margin-bottom:5px;">Call Only After Success</strong>Ensure payment status is <span style="color:#059669; font-weight:bold;">"COMPLETED"</span> in your verified systems.</td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 10px 25px 40px 25px;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" bgcolor="#0f172a" style="border-radius:24px; padding:48px 32px; text-align:center;">
                                                        <tr><td style="color:#ffffff; font-size:26px; font-weight:800; padding-bottom:16px; letter-spacing:-0.5px;">Need implementation assistance?</td></tr>
                                                        <tr><td style="color:#94a3b8; font-size:15px; line-height:1.6; padding-bottom:32px;">Our developer team is available 24/7 to help you troubleshoot any integration bottlenecks.</td></tr>
                                                        <tr>
                                                            <td>
                                                                <a href="mailto:support@renaissance.ac.in" style="background-color:#f97316; border-radius:12px; color:#ffffff; display:inline-block; font-size:14px; font-weight:700; line-height:50px; text-align:center; text-decoration:none; padding:0 30px; text-transform:uppercase; letter-spacing:1px; box-shadow: 0 10px 15px -3px rgba(249, 115, 22, 0.4); -webkit-text-size-adjust:none;">
                                                                    <img src="https://img.icons8.com/ios-filled/16/ffffff/headset.png" width="16" height="16" style="vertical-align:middle; margin-right:8px; margin-top:-2px;">
                                                                    <span style="vertical-align:middle;">CONTACT SUPPORT</span>
                                                                </a>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr><td align="center" style="padding-bottom:40px; font-style:italic; color:#6b7280; font-size:14px;">Happy coding,<br><strong style="color:#4f46e5; font-style:normal;">The AreYouReporting Team</strong></td></tr>
                                            <tr>
                                                <td bgcolor="#f9fafb" style="padding: 30px 25px; border-top:1px solid #e5e7eb;">
                                                    <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                        <tr>
                                                            <td class="mob-stack" style="font-size:12px; color:#9ca3af;">© %3$d. Renaissance University. ALL RIGHTS RESERVED.</td>
                                                            <td class="mob-stack" align="right" style="padding-top:10px;">
                                                                <img src="https://img.icons8.com/ios-filled/20/9ca3af/facebook-new.png" width="18" height="18" style="margin-left:15px;">
                                                                <img src="https://img.icons8.com/ios-filled/20/9ca3af/twitter.png" width="18" height="18" style="margin-left:15px;">
                                                                <img src="https://img.icons8.com/ios-filled/20/9ca3af/linkedin.png" width="18" height="18" style="margin-left:15px;">
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </body>
                        </html>
                        """,
                apiKey, rawSecret, year);
    }

    // ----------------------- HELPERS -----------------------

    private void validateEmailParams(String toEmail, String required) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }
        if (required == null || required.isBlank()) {
            throw new IllegalArgumentException("Email body parameter is required");
        }
    }

    private void sendHtmlEmailWithInlineLogo(String toEmail, String subject, String htmlContent)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        try {
            helper.setFrom(fromAddress, fromName);
        } catch (UnsupportedEncodingException e) {
            helper.setFrom(fromAddress);
        }

        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        try {
            ClassPathResource logo = new ClassPathResource("static/images/renaissance-logo.png");
            if (logo.exists()) {
                helper.addInline("logoImage", logo);
            } else {
                log.debug("Logo resource not found");
            }
        } catch (Exception ex) {
            log.warn("Failed to attach logo: {}", ex.getMessage());
        }

        mailSender.send(message);
    }

    private String buildOtpHtml(String otp) {
        int year = java.time.LocalDate.now().getYear();
        return String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Email Verification OTP</title>
                    <style>
                        body { margin: 0; padding: 0; background-color: #f4f7ff; font-family: 'Inter', Segoe UI, Roboto, sans-serif; }
                        table { border-collapse: collapse !important; }
                        .container { width: 100%% !important; max-width: 560px !important; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; }
                    </style>
                </head>
                <body style="margin:0;padding:24px 0;background-color:#f4f7ff;">
                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" bgcolor="#f4f7ff">
                        <tr><td align="center" style="padding: 24px 16px;">
                            <table width="560" class="container" border="0" cellspacing="0" cellpadding="0" bgcolor="#ffffff" style="max-width:560px;border-radius:16px;box-shadow:0 4px 24px rgba(99,102,241,0.10);overflow:hidden;">
                                <!-- Header -->
                                <tr>
                                    <td style="background:linear-gradient(135deg,#6366f1 0%%,#4f46e5 100%%);padding:28px 32px;">
                                        <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td><img src="cid:logoImage" alt="CMS" style="display:block;height:28px;filter:brightness(0) invert(1);" onerror="this.style.display='none'"></td>
                                                <td align="right" style="color:#c7d2fe;font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;">Email Verification</td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Hero -->
                                <tr>
                                    <td style="background:linear-gradient(135deg,#6366f1 0%%,#4f46e5 100%%);padding:32px 32px 48px 32px;text-align:center;">
                                        <table width="64" height="64" border="0" cellspacing="0" cellpadding="0" bgcolor="rgba(255,255,255,0.18)" style="border-radius:50%%;margin:0 auto 20px auto;">
                                            <tr><td align="center" valign="middle" style="font-size:28px;">🔐</td></tr>
                                        </table>
                                        <h1 style="margin:0 0 10px 0;color:#ffffff;font-size:26px;font-weight:800;letter-spacing:-0.5px;">Verify Your New Email</h1>
                                        <p style="margin:0;color:#c7d2fe;font-size:15px;line-height:1.6;">Use the one-time code below to confirm your new email address.</p>
                                    </td>
                                </tr>
                                <!-- OTP Box -->
                                <tr>
                                    <td style="padding:0 32px;margin-top:-24px;">
                                        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background:#ffffff;border:2px solid #e0e7ff;border-radius:16px;margin-top:-24px;box-shadow:0 4px 20px rgba(99,102,241,0.12);">
                                            <tr>
                                                <td style="padding:32px;text-align:center;">
                                                    <p style="margin:0 0 8px 0;font-size:12px;font-weight:700;color:#6366f1;text-transform:uppercase;letter-spacing:1.5px;">Your Verification Code</p>
                                                    <div style="background:linear-gradient(135deg,#eef2ff 0%%,#f5f3ff 100%%);border-radius:12px;padding:20px 32px;display:inline-block;margin:8px 0;">
                                                        <span style="font-size:42px;font-weight:900;color:#4f46e5;letter-spacing:12px;font-family:'Courier New',monospace;">%s</span>
                                                    </div>
                                                    <p style="margin:16px 0 0 0;font-size:13px;color:#6b7280;">
                                                        ⏱ This code expires in <strong style="color:#ef4444;">15 minutes</strong>
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Instructions -->
                                <tr>
                                    <td style="padding:28px 32px 8px 32px;">
                                        <table width="100%%" border="0" cellspacing="0" cellpadding="0" bgcolor="#fef2f2" style="border-radius:12px;border:1px solid #fee2e2;">
                                            <tr>
                                                <td style="padding:16px 20px;">
                                                    <p style="margin:0;font-size:13px;color:#991b1b;line-height:1.6;">
                                                        <strong>⚠ Do not share this code</strong> with anyone. Renaissance University will never ask for your OTP via phone, email, or chat.
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Help Text -->
                                <tr>
                                    <td style="padding:16px 32px 32px 32px;text-align:center;">
                                        <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
                                            Didn't request this? You can safely ignore this email — your current email address will remain unchanged.
                                        </p>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td bgcolor="#f9fafb" style="padding:20px 32px;border-top:1px solid #e5e7eb;text-align:center;">
                                        <p style="margin:0;font-size:11px;color:#9ca3af;">© %d Renaissance University · Chancellor's Office · All rights reserved.</p>
                                    </td>
                                </tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """, otp, year);
    }


    private String buildSimpleHtml(String message) {
        return "<html><body style='font-family:Segoe UI'>" + "<p>" + message + "</p>" + "</body></html>";
    }
}