# NezamMonitor Android Data Format

## Format Version
**NezamMonitor.AndroidData** v1.0

## File Structure
```
package.nzmdata/
│
├── manifest.json          ← اطمینان‌بخش نسخه و checksum
├── data.json              ← داده‌های اصلی
├── metadata.json          ← متادیتای محیطی (نسخه‌ی برنامه، تاریخ، etc)
├── schema.json            ← تعریف کامل schemas (برای diagnostic)
└── attachments/
    ├── reports/           ← پرونده‌های Word Report (اختیاری)
    └── documents/         ← سایر فایل‌های ضمیمه (اختیاری)
```

## File Formats

### 1. manifest.json
حداقل:
```json
{
  "format": "NezamMonitor.AndroidData",
  "version": "1.0",
  "formatVersion": "1",
  "createdAt": "ISO-8601 timestamp (T00:00:00Z UTC)",
  "sourceApplication": "NezamMonitor Windows",
  "sourceApplicationVersion": "...",
  "sourceDatabasePath": "... (optional)",
  "recordCount": 0,
  "checksum": "SHA-256 of archive",
  "editorProfiles": ["list_of_edit_ids"]
}
```

### 2. data.json
ساختار:
```json
{
  "cases": [...],
  "engineers": [...],
  "fees": [...],
  "reports": [...],
  "followUpEdits": [...],
  "followUpGroups": [...]
}
```

### 3. metadata.json
```json
{
  "versionApp": "...",
  "versionPlatform": "...",
  "exportTime": "ISO-8601",
  "userId": "... (optional, anonymized)",
  "currency": "IRR",
  "dateStandard": "Persian Solar Hijri"
}
```

### 4. schema.json
```json
{
  "Case": { ... },
  "Engineer": { ... },
  "Fee": { ... },
  "ReportRecord": { ... },
  "FollowUpEdit": { ... }
}
```

## Entity Definitions

### Case
{
  "caseId": "long",
  "caseNumber": "string (PK)",
  "serial": "string",
  "owner": "string",
  "ownerMobile": "string (for dialing)",
  "responsibility": "string",
  "capacityDate": "string (Persian date)",
  "office": "string",
  "reportDate1": "string",
  "reportDate2": "string",
  "reportDate3": "string",
  "specification": {
    "address": "string",
    "group": "string",
    ...
  },
  "engineers": ["engineerId_x"],
  "fees": ["feeId_x"],
  "reports": ["reportId_x"]
}

### Engineer
{ "id": "long", ... }

### Fee
{ "id": "long", ... }

### ReportRecord
{ "id": "long", ... }

### FollowUpEdit
{ "caseNumber": "string", "description": "string", ... }
```

