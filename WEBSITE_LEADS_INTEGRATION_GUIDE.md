# DDS Secure Website Leads Integration API Guide

## 1. Overview
This document defines the technical contract for integrating the University Website forms directly with the **Data Distribution System (DDS)**. 

The integration exposes a single, high-performance, secure REST API that accepts leads across all website forms (Campus Tour, Contact Query, Placement, Course Enquiry, Fees Inquiry, University Comparison, Virtual Tour, Google Ads, Newspaper, and future forms).

---

## 2. Architecture & Security Model

```text
┌─────────────────────────────────┐
│     University Website Forms    │
│ (Campus Tour, Fees Inquiry, etc)│
└────────────────┬────────────────┘
                 │ (Form Submit)
                 ▼
┌─────────────────────────────────┐
│  University Website Web Server  │
│      (Node.js / PHP / Python)   │
└────────────────┬────────────────┘
                 │ Header: X-Website-API-Key: <SECRET>
                 │ Endpoint: POST /api/integrations/website/leads
                 ▼
┌────────────────────────────────────────────────────────┐
│             DDS Website Integration API                │
│  - Constant-time API Key Validation (Fails closed)     │
│  - IP / Key Sliding-Window Rate Limiting (60 req/min)  │
│  - Concurrency-safe Submission Idempotency Check       │
└────────────────┬───────────────────────────────────────┘
                 │ Canonical Ingestion & Academic Resolver
                 ▼
┌────────────────────────────────────────────────────────┐
│             DDS Canonical Lead Pipeline                │
│  - Maps to "Website - <Form Name>" Lead Source         │
│  - Resolves Course / Program IDs from Academic Catalog │
│  - System User Audit Logging & Initial Status History  │
│  - Unassigned Inbound Pool Persistence                 │
└────────────────────────────────────────────────────────┘
```

> **CRITICAL SECURITY REQUIREMENT: NEVER EXPOSE THE API KEY IN CLIENT-SIDE CODE**
> - The integration API key must **never** be included in React, Angular, Vue, plain browser JavaScript, HTML source code, URL query parameters, or local storage.
> - Client submissions must post to your own website backend (server-side proxy), which then attaches the `X-Website-API-Key` header and forwards the request server-to-server to DDS.

---

## 3. Endpoint Specifications

- **HTTP Method:** `POST`
- **Route:** `/api/integrations/website/leads`
- **Content-Type:** `application/json`
- **Accept:** `application/json`

### Required Request Headers

| Header Name | Type | Description |
| :--- | :--- | :--- |
| `X-Website-API-Key` | String | Dedicated integration secret key configured in DDS (`app.integrations.website.api-key`). |
| `Content-Type` | String | Must be `application/json` |

---

## 4. Supported Website Source Codes

Every incoming lead must supply a registered `sourceCode`. DDS automatically maps each stable code to its canonical database `LeadSource` record prefixed with `Website - `:

| Source Code (`sourceCode`) | Canonical DDS Lead Source Label | Form / Channel Description |
| :--- | :--- | :--- |
| `WEBSITE_CAMPUS_TOUR` | **Website - Campus Tour** | Campus Tour Booking & Visit Inquiry |
| `WEBSITE_CONTACT_QUERY` | **Website - Contact Query** | General Contact Us Form / Query |
| `WEBSITE_PLACEMENT` | **Website - Placement** | Placement Report & Career Inquiries |
| `WEBSITE_COURSE_ENQUIRY` | **Website - Course Enquiry** | Academic Course Program Inquiry |
| `WEBSITE_FEES_INQUIRY` | **Website - Fees Inquiry** | Tuition & Hostel Fees Information Request |
| `WEBSITE_UNIVERSITY_COMPARISON` | **Website - University Comparison** | University Comparison & Evaluation Tool |
| `WEBSITE_VIRTUAL_TOUR` | **Website - Virtual Tour** | Virtual Tour / 360 Degree View Form |
| `WEBSITE_GOOGLE_ADS` | **Website - Google Ads** | Website Landing Page (Google Search/Display Ads) |
| `WEBSITE_NEWSPAPER` | **Website - Newspaper** | Print Media & Newspaper QR Code Inbound Leads |

*Note: For `WEBSITE_CAMPUS_TOUR` and `WEBSITE_VIRTUAL_TOUR`, DDS automatically marks the lead's visit flag (`planningToVisitUniversity = true`).*

---

## 5. Request Payload Contract

### JSON Schema & Field Definitions

```json
{
  "sourceCode": "WEBSITE_FEES_INQUIRY",
  "externalLeadId": "fees-form-2026-987654",
  "name": "Rahul Sharma",
  "mobile": "9876543210",
  "email": "rahul.sharma@example.com",
  "courseInterested": "MBA",
  "message": "I would like information about MBA fee structure and scholarships.",
  "city": "Indore",
  "state": "Madhya Pradesh",
  "country": "India",
  "websiteUrl": "https://university.edu/admissions/fees",
  "consent": true,
  "utmSource": "google",
  "utmMedium": "cpc",
  "utmCampaign": "mba_admissions_2026"
}
```

### Field Specifications

| Field Name | Type | Required? | Max Length | Notes / Aliases |
| :--- | :--- | :--- | :--- | :--- |
| `sourceCode` | String | **Yes** | 50 | Stable code from the table above. E.g. `WEBSITE_FEES_INQUIRY`. |
| `externalLeadId` | String | **Recommended** | 100 | Unique ID generated by your form submission system. Enables **strict idempotency & retry safety**. |
| `name` | String | **Yes** | 150 | Lead's full name. *(Alias: `fullName`, `studentName`)* |
| `mobile` | String | **Yes** | 20 | 7–20 digits/symbols (`+`, `(`, `)`, `-`). *(Alias: `phoneNumber`, `phone`)* |
| `email` | String | No | 100 | Standard valid email format. |
| `courseInterested` | String | No | 150 | Course name or code (e.g. `MBA`, `B.Tech`). DDS matches against active courses/programs automatically. *(Alias: `course`, `program`)* |
| `message` | String | No | 2000 | Inquiry remarks or message. Preserved in DDS lead audit history. *(Alias: `remarks`, `query`)* |
| `city` | String | No | 100 | City name (e.g. `Indore`). |
| `state` | String | No | 100 | State name (e.g. `Madhya Pradesh`). |
| `country` | String | No | 100 | Defaults to `"India"` if omitted. |
| `websiteUrl` | String | No | 255 | URL of the page where the form was submitted. |
| `consent` | Boolean | No | - | Consent agreement flag (`true`/`false`). |
| `utmSource` | String | No | 100 | Marketing attribution source (`google`, `facebook`, etc.). |
| `utmMedium` | String | No | 100 | Marketing medium (`cpc`, `organic`, etc.). |
| `utmCampaign` | String | No | 100 | Campaign identifier. |

---

## 6. Response Codes & Envelopes

### 6.1. Successfully Created Lead (HTTP 201 Created)
Returned when a submission is received and a new lead is created in DDS.

```json
{
  "success": true,
  "message": "Website lead received successfully",
  "data": {
    "externalLeadId": "fees-form-2026-987654",
    "leadId": "7b8f9e62-c104-4b5b-871d-5a8e0f6c2e11",
    "leadCode": "LEAD-F8C491A2",
    "sourceCode": "WEBSITE_FEES_INQUIRY",
    "status": "CREATED",
    "message": "Website lead received successfully"
  },
  "status": 201,
  "timestamp": "2026-10-27 20:15:30"
}
```

### 6.2. Idempotent Retry of Existing Submission (HTTP 200 OK)
Returned when a client retries delivery with an already processed `externalLeadId` and `sourceCode`. **No duplicate lead is created in the database.**

```json
{
  "success": true,
  "message": "Website lead already processed (idempotent submission)",
  "data": {
    "externalLeadId": "fees-form-2026-987654",
    "leadId": "7b8f9e62-c104-4b5b-871d-5a8e0f6c2e11",
    "leadCode": "LEAD-F8C491A2",
    "sourceCode": "WEBSITE_FEES_INQUIRY",
    "status": "EXISTING_SUBMISSION",
    "message": "Website lead already processed (idempotent submission)"
  },
  "status": 200,
  "timestamp": "2026-10-27 20:16:05"
}
```

### 6.3. Authentication Failure (HTTP 401 Unauthorized)
Returned if the `X-Website-API-Key` header is missing, invalid, or if the server secret is unconfigured (fails closed).

```json
{
  "success": false,
  "message": "Invalid Website Integration API Key",
  "data": null,
  "status": 401,
  "timestamp": "2026-10-27 20:16:20"
}
```

### 6.4. Validation Error (HTTP 400 Bad Request)
Returned when required fields are missing or unsupported source codes are provided.

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "name": "Name is required",
    "mobile": "Mobile number is required"
  },
  "status": 400,
  "timestamp": "2026-10-27 20:16:45"
}
```

Or for an unsupported source code:
```json
{
  "success": false,
  "message": "Unsupported or invalid website source code: UNKNOWN_FORM_XYZ",
  "data": null,
  "status": 400,
  "timestamp": "2026-10-27 20:17:01"
}
```

### 6.5. Rate Limit Exceeded (HTTP 429 Too Many Requests)
Returned if request volume from a client exceeds the configured rate limit (default 60 requests/minute + burst allowance).

```json
{
  "success": false,
  "message": "Rate limit exceeded. Please try again later.",
  "data": null,
  "status": 429,
  "timestamp": "2026-10-27 20:17:30"
}
```

---

## 7. Server-Side Integration Examples

### Node.js (Express / Backend Proxy)
```javascript
const axios = require('axios');

async function forwardLeadToDds(formData) {
  const ddsApiUrl = process.env.DDS_BASE_URL + '/api/integrations/website/leads';
  const apiKey = process.env.WEBSITE_LEADS_API_KEY; // Kept secure in server environment variable

  const payload = {
    sourceCode: formData.sourceCode,
    externalLeadId: formData.submissionId || ('web-' + Date.now()),
    name: formData.name,
    mobile: formData.phone,
    email: formData.email,
    courseInterested: formData.course,
    message: formData.message,
    city: formData.city,
    state: formData.state,
    websiteUrl: formData.currentUrl,
    consent: true
  };

  try {
    const response = await axios.post(ddsApiUrl, payload, {
      headers: {
        'X-Website-API-Key': apiKey,
        'Content-Type': 'application/json'
      },
      timeout: 10000
    });
    return response.data;
  } catch (error) {
    console.error('DDS Lead Forwarding Error:', error.response?.data || error.message);
    throw error;
  }
}
```

### PHP (Server-Side cURL)
```php
<?php
function submitLeadToDds($formData) {
    $ddsUrl = getenv('DDS_BASE_URL') . '/api/integrations/website/leads';
    $apiKey = getenv('WEBSITE_LEADS_API_KEY');

    $payload = json_encode([
        'sourceCode'       => $formData['sourceCode'],
        'externalLeadId'   => $formData['id'] ?? ('web-' . uniqid()),
        'name'             => $formData['name'],
        'mobile'           => $formData['phone'],
        'email'            => $formData['email'] ?? null,
        'courseInterested' => $formData['course'] ?? null,
        'message'          => $formData['message'] ?? null,
        'city'             => $formData['city'] ?? null,
        'state'            => $formData['state'] ?? null,
        'websiteUrl'       => $formData['pageUrl'] ?? null,
        'consent'          => true
    ]);

    $ch = curl_init($ddsUrl);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        'Content-Type: application/json',
        'X-Website-API-Key: ' . $apiKey
    ]);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);

    $result = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    return json_decode($result, true);
}
```

---

## 8. Configuration & Future Website Forms

### 8.1 Server Configuration (`application.properties` / Environment Variables)
```properties
# Enable / disable endpoint
app.integrations.website.enabled=${WEBSITE_INTEGRATION_ENABLED:true}

# Secret API key (Keep this secure; set via environment variable)
app.integrations.website.api-key=${WEBSITE_LEADS_API_KEY}

# Audit user in DDS
app.integrations.website.system-username=${WEBSITE_SYSTEM_USERNAME:superadmin}

# Rate limiting
app.integrations.website.rate-limit-per-minute=${WEBSITE_RATE_LIMIT_PER_MINUTE:60}
app.integrations.website.rate-limit-burst=${WEBSITE_RATE_LIMIT_BURST:10}
```

### 8.2 Adding a New Website Form in the Future
To introduce a new website form:
1. Add the new Lead Source in the DDS admin panel (`/api/lead-sources`) or `DatabaseSeeder`:
   - Name: `Website - <Form Name>`
   - Code: `WEBSITE_<NEW_FORM_CODE>`
2. Add the corresponding enum entry in `WebsiteSourceCode.java` if strict code listing is preferred.
3. The single API endpoint will automatically resolve and ingest leads for that new form without altering other services or requiring separate endpoints!
