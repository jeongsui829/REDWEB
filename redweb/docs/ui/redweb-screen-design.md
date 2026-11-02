# RedWeb Screen Design Blueprint

## 1. Service Positioning

RedWeb is designed as a community-driven web portal that feels like a production-ready content service. The public-facing UI should communicate trust, structure, and activity rather than experimentation. The fact that the system is used in an isolated validation environment is an operational note, not a visual identity.

## 2. Visual Direction

- Brand tone: structured, confident, editorial, community-centric
- Palette:
  - Primary: deep red `#9f1239`
  - Secondary: navy slate `#1f2a44`
  - Accent: amber `#f59e0b`
  - Surface: warm white `#fcfaf8`
  - Border: soft gray `#e5e7eb`
- Layout style:
  - sticky top navigation
  - wide content shell with left main rail and right utility rail
  - information-dense cards with clean padding and subtle shadows
  - noticeable hierarchy for notices, popular items, and account widgets
- Typography:
  - page title: bold and editorial
  - section title: medium weight with compact spacing
  - metadata: smaller muted text
- Interaction:
  - hover elevation for cards
  - chip-style filters
  - noticeable focus styles on fields and buttons
  - loading spinner and inline validation feedback

## 3. Core Screen Composition

### Home

- Hero strip with service message, search bar, and current date/time widget
- rotating notice/banner carousel
- main feed area:
  - pinned notices
  - trending posts
  - latest posts
  - category quick links
- right rail:
  - login/profile widget
  - notification preview
  - recent views
  - keyword tags
  - service stats

### Board List

- breadcrumb
- board title and board description
- category tabs
- search row with keyword, type, date range, sort
- notice block pinned above list
- post table with status chips, tags, counts, author, created date
- right rail with best posts and board-specific notice

### Post Detail

- breadcrumb
- post title block with author meta, counts, tags
- content body with image preview gallery
- attachment download list
- recommendation bar
- author summary card
- comments thread with nested replies
- adjacent post navigation

### Login / Register

- split panel layout
- left side: trust-building service message, highlights, policy note
- right side: clean form card with validation messaging
- footer links to find ID, find password, and email verification

### My Page

- sidebar navigation
- top profile card
- activity summary cards
- tabs/sections:
  - profile settings
  - password change
  - login history
  - notifications
  - my posts
  - my comments
  - withdrawal

### Admin Dashboard

- dedicated admin shell
- KPI cards
- trend chart placeholder region prepared for future JS chart integration
- recent reports table
- recent error log table
- recent sign-up and access overview

## 4. Menu Blueprint

### Global Navigation

- Home
- Notices
- Free Board
- Knowledge Share
- Q&A
- Archive
- Popular
- Tags

### Account Navigation

- Login
- Register
- My Page
- Notifications
- Logout

### My Page Navigation

- Account Overview
- Edit Profile
- Change Password
- Login History
- Notifications
- My Posts
- My Comments
- Withdraw

### Admin Navigation

- Dashboard
- Members
- Posts
- Reports
- Files
- Notices
- Access Logs
- Error Logs
- Statistics
- Roles

## 5. User Journey Summary

1. Visitor lands on active-looking home dashboard.
2. Visitor browses notices, trending posts, and category feeds.
3. Visitor opens post detail and is encouraged to log in for interaction.
4. Authenticated member writes posts, comments, uploads files, and manages profile.
5. Member returns through notifications, recent views, and activity summaries.

## 6. Admin Journey Summary

1. Admin signs in through dedicated entry.
2. Dashboard reveals operational status at a glance.
3. Admin reviews reports, notices, files, logs, and trend metrics.
4. Admin actions remain separated from public layout and navigation.

## 7. Implementation Notes

- Public JSP fragments must be reusable through include files.
- Admin JSP fragments must use a dedicated layout system.
- CSS should stay separated into common, layout, component, page, and admin bundles.
- Controllers should remain thin and delegate page preparation to services.
- Oracle SQL should be isolated under resources/sql for future secure-version comparison.
