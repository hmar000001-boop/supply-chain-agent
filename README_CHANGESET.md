# Apply CI Maven HTTP 429 fix

Extract this change-only ZIP at the repository root, preserving paths. It contains the modified Maven wrapper scripts/properties and GitHub Actions workflow plus new bootstrap documentation.

After applying, commit the changes and rerun the Java CI workflow. The wrapper downloads Maven 3.9.16 directly from Apache distribution infrastructure, verifies SHA-512 and caches the distribution. A future 429 while Maven resolves project dependencies would be a separate Maven repository issue.
