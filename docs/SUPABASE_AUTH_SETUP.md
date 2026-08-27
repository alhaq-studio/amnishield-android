# Supabase Authentication & Email Template Setup Guide

This guide details how to configure Supabase Authentication for AmniShield to ensure that **6-digit numerical OTP codes** (`{{ .Token }}`) are sent in all emails and that verification links automatically launch the Android app or activate the user on the web.

---

## 📧 1. Supabase Cloud Dashboard Email Template Setup

In your [Supabase Dashboard](https://supabase.com/dashboard) under **Project > Authentication > Email Templates**:

### A. Magic Link / OTP Email
- **Subject**: `Your AmniShield Verification Code: {{ .Token }}`
- **Body**:
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Your AmniShield Verification Code</title>
</head>
<body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background-color:#f4f6f9;margin:0;padding:0;color:#1e293b;">
  <div style="max-width:540px;margin:32px auto;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.05);border:1px solid #e2e8f0;">
    <div style="background:linear-gradient(135deg,#0f172a 0%,#1e3a8a 100%);padding:32px 24px;text-align:center;color:#ffffff;">
      <div style="display:inline-block;width:48px;height:48px;background:rgba(255,255,255,0.15);border-radius:12px;margin-bottom:12px;font-size:24px;line-height:48px;">🛡️</div>
      <h1 style="margin:0;font-size:22px;font-weight:700;">AmniShield Verification</h1>
    </div>
    <div style="padding:32px 24px;text-align:center;">
      <p style="font-size:15px;line-height:1.6;color:#475569;margin:0 0 20px 0;">Here is your 6-digit one-time passcode to sign in or activate your AmniShield app.</p>
      
      <!-- 6-Digit Code Highlight -->
      <div style="background:#f0fdf4;border:2px dashed #22c55e;border-radius:12px;padding:18px 24px;margin:20px auto;display:inline-block;">
        <div style="font-size:11px;text-transform:uppercase;letter-spacing:1.5px;color:#166534;font-weight:700;margin-bottom:6px;">Your 6-Digit Verification Code</div>
        <div style="font-family:'SFMono-Regular',Consolas,Menlo,monospace;font-size:34px;font-weight:800;color:#0f172a;letter-spacing:8px;margin:0;">{{ .Token }}</div>
      </div>

      <p style="font-size:13px;color:#64748b;margin:6px 0 24px 0;">Enter this code in your Android app or web console. Code expires in 60 minutes.</p>

      <div style="border-top:1px solid #e2e8f0;margin:24px 0 20px 0;"></div>
      <p style="font-weight:600;color:#1e293b;margin-bottom:8px;">Or verify automatically with 1-tap:</p>
      
      <a href="{{ .ConfirmationURL }}" style="display:inline-block;width:100%;box-sizing:border-box;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:600;font-size:15px;padding:14px 24px;border-radius:10px;margin-top:8px;">
        ⚡ Verify &amp; Activate AmniShield
      </a>

      <a href="amnishield://activate?token={{ .Token }}&email={{ .Email }}" style="display:inline-block;width:100%;box-sizing:border-box;background:#f1f5f9;color:#334155;text-decoration:none;font-weight:600;font-size:14px;padding:12px 24px;border-radius:10px;margin-top:10px;border:1px solid #cbd5e1;">
        📱 Open Directly in Android App
      </a>
    </div>
    <div style="background:#f8fafc;padding:18px 24px;text-align:center;font-size:12px;color:#94a3b8;border-top:1px solid #f1f5f9;">
      <p style="margin:0 0 4px 0;">If you did not request this code, please ignore this email.</p>
      <p style="margin:0;">&copy; AmniShield &bull; Al-Haq Initiative &bull; Zero-Knowledge Protection</p>
    </div>
  </div>
</body>
</html>
```

---

### B. Confirm Signup Template
- **Subject**: `Confirm Your AmniShield Account (Code: {{ .Token }})`
- **Body**: Same structure as Magic Link using `{{ .Token }}` and `{{ .ConfirmationURL }}`.

---

## 🌐 2. Supabase Auth URL Configuration

Under **Authentication > URL Configuration**:
- **Site URL**: `https://app.amnishield.com`
- **Redirect URLs (Allow list)**:
  - `https://app.amnishield.com`
  - `https://app.amnishield.com/*`
  - `https://app.amnishield.com/activate`
  - `https://app.amnishield.com/app/`
  - `https://amnishield.com`
  - `https://amnishield.com/*`
  - `https://amnishield.com/activate`
  - `amnishield://activate`
  - `amnishield://auth`
  - `amnishield://activate`
  - `http://localhost:*`
