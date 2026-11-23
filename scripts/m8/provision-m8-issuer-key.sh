#!/usr/bin/env bash
# Provision exactly one M8 role key. Run separately under that role's authorized custodian.
set -euo pipefail
umask 077

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/m8/provision-m8-issuer-key.sh <role> <private-key-directory> <role-public-export-directory>

Roles and fixed issuer IDs:
  endpoint  m8-endpoint-v1
  safety    m8-safety-v1
  ci        m8-ci-v1
  b1        m8-b1-v1
  b2        m8-b2-v1
  b3        m8-b3-v1
  b6        m8-b6-v1

The private key is encrypted and must be stored outside the Git repository. Use a separate
private directory and public-export directory for each role. A separate trust-root administrator
collects the seven exported .pub files into the final pinned-public-key directory.
EOF
  exit 2
}

[[ $# -eq 3 ]] || usage
role=$1
private_dir_input=$2
public_dir_input=$3
case "$role" in
  endpoint) issuer=m8-endpoint-v1 ;;
  safety) issuer=m8-safety-v1 ;;
  ci) issuer=m8-ci-v1 ;;
  b1) issuer=m8-b1-v1 ;;
  b2) issuer=m8-b2-v1 ;;
  b3) issuer=m8-b3-v1 ;;
  b6) issuer=m8-b6-v1 ;;
  *) echo "Unknown M8 issuer role: $role" >&2; usage ;;
esac

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repo_root=$(cd -- "$script_dir/../.." && pwd -P)

outside_repo() {
  local candidate=$1
  case "$candidate/" in
    "$repo_root/"*) echo "Key material and pinned public keys must be stored outside the repository: $candidate" >&2; exit 1 ;;
  esac
}
# Canonicalize even not-yet-created paths and reject repository destinations before mkdir.
private_dir=$(realpath -m -- "$private_dir_input")
public_dir=$(realpath -m -- "$public_dir_input")
outside_repo "$private_dir"
outside_repo "$public_dir"
[[ "$private_dir" != "$public_dir" ]] || { echo "Private and public key directories must differ" >&2; exit 1; }
case "$private_dir/" in "$public_dir/"*) echo "Private-key directory cannot be inside the pinned public-key directory" >&2; exit 1;; esac
case "$public_dir/" in "$private_dir/"*) echo "Pinned public-key directory cannot be inside the private-key directory" >&2; exit 1;; esac
mkdir -p -- "$private_dir" "$public_dir"
private_dir=$(cd -- "$private_dir" && pwd -P)
public_dir=$(cd -- "$public_dir" && pwd -P)
[[ ! -L "$private_dir" && ! -L "$public_dir" ]] || { echo "Key directories cannot be symlinks" >&2; exit 1; }

private_file="$private_dir/$issuer.pem"
public_file="$public_dir/$issuer.pub"
[[ ! -e "$private_file" && ! -L "$private_file" && ! -e "$public_file" && ! -L "$public_file" ]] || {
  echo "Refusing to overwrite an existing key file for $issuer" >&2; exit 1;
}

command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is required" >&2; exit 1; }
command -v mktemp >/dev/null 2>&1 || { echo "mktemp is required" >&2; exit 1; }
private_tmp=$(mktemp "$private_dir/.$issuer.private.tmp.XXXXXX")
public_tmp=$(mktemp "$public_dir/.$issuer.public.tmp.XXXXXX")
chmod 600 "$private_tmp" "$public_tmp"
private_created=0
public_created=0
cleanup() {
  rm -f -- "$private_tmp" "$public_tmp"
  # If publication fails between the two files, do not leave a half-provisioned identity.
  (( public_created == 0 )) || rm -f -- "$public_file"
  (( private_created == 0 )) || rm -f -- "$private_file"
}
trap cleanup EXIT

printf 'Provisioning one Ed25519 issuer: %s (%s)\n' "$role" "$issuer"
printf 'Choose a strong passphrase. OpenSSL prompts twice to create the encrypted key and once more to export its public key; re-enter the same passphrase.\n'
openssl genpkey -algorithm ED25519 -aes-256-cbc -out "$private_tmp"
chmod 600 "$private_tmp"
openssl pkey -in "$private_tmp" -pubout -out "$public_tmp"
chmod 644 "$public_tmp"

# Validate the exported public key and derive the same DER SHA-256 used by Java's trust composition.
public_fingerprint=$(openssl pkey -pubin -in "$public_tmp" -outform DER | sha256sum | awk '{print $1}')
[[ "$public_fingerprint" =~ ^[0-9a-f]{64}$ ]] || { echo "Could not fingerprint exported Ed25519 public key" >&2; exit 1; }

# Both paths are created only after all input and cryptographic checks have passed.
( set -o noclobber; cat "$private_tmp" > "$private_file" )
private_created=1
chmod 600 "$private_file"
( set -o noclobber; cat "$public_tmp" > "$public_file" )
public_created=1
chmod 644 "$public_file"
rm -f -- "$private_tmp" "$public_tmp"
private_created=0
public_created=0
trap - EXIT

printf '\nCreated issuer identity %s\n' "$issuer"
printf 'Private key: %s (encrypted PEM; mode 0600; retain under this custodian only)\n' "$private_file"
printf 'Public pin:  %s\n' "$public_file"
printf 'DER SHA-256: %s\n' "$public_fingerprint"
printf '\nTransfer only the .pub file to the trust-root administrator. Never commit or email the private key/passphrase.\n'
