#!/usr/bin/env bash
# SovNode 2026, Apache License 2.0; see NOTICE.
# Local onboarding for the security-hardened configuration.
# Generates local-only credentials, writes local.env and local-bootstrap.sql,
# and proves the documented path with --self-test. Never prints generated
# secret values except the one-time initial admin password in real mode.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_OUT="${ENV_OUT:-$REPO_ROOT/local.env}"
SQL_OUT="${SQL_OUT:-$REPO_ROOT/local-bootstrap.sql}"
PUB_OUT="${PUB_OUT:-$REPO_ROOT/local_rsa_public.b64}"
ADMIN_USER_ID=1

gen_password() {
    python3 - "${1:-16}" <<'PY'
import secrets, sys
alpha = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
print("".join(secrets.choice(alpha) for _ in range(int(sys.argv[1]))))
PY
}

gen_jwt_secret() {
    head -c 64 /dev/urandom | base64 -w0
}

gen_des_material() {
    python3 - <<'PY'
import secrets
alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
print("".join(secrets.choice(alpha) for _ in range(8)))
PY
}

gen_rsa_private() {
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER 2>/dev/null | base64 -w0
}

bcrypt_hash() {
    python3 - "$1" <<'PY'
import bcrypt, sys
print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(rounds=10)).decode())
PY
}

bcrypt_check() {
    python3 - "$1" "$2" <<'PY'
import bcrypt, sys
raise SystemExit(0 if bcrypt.checkpw(sys.argv[1].encode(), sys.argv[2].encode()) else 1)
PY
}

write_env() {
    umask 077
    cat > "$ENV_OUT" <<ENV
SPRING_PROFILES_ACTIVE=dev
DB_USER=${DB_USER:-root}
DB_PWD=${DB_PWD:?set DB_PWD before running}
JWT_SECRET=$1
RSA_PRIVATE_KEY=$2
ELADMIN_DES_KEY=$3
S3_ACCESS_KEY=${S3_ACCESS_KEY:-}
S3_SECRET_KEY=${S3_SECRET_KEY:-}
DRUID_CONSOLE_ENABLED=${DRUID_CONSOLE_ENABLED:-false}
DRUID_CONSOLE_ALLOW=${DRUID_CONSOLE_ALLOW:-127.0.0.1}
DRUID_CONSOLE_USER=${DRUID_CONSOLE_USER:-}
DRUID_CONSOLE_PWD=${DRUID_CONSOLE_PWD:-}
ENV
}

self_test() {
    local tmp jwt rsa des admin_pw hash sql_tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    jwt="$(gen_jwt_secret)"
    rsa="$(gen_rsa_private)"
    des="$(gen_des_material)"
    admin_pw="$(gen_password 16)"

    [ "${#jwt}" -ge 86 ] || { echo "FAIL jwt secret length"; return 1; }
    [ "${#des}" -eq 8 ] || { echo "FAIL des material length"; return 1; }
    printf '%s' "$rsa" | base64 -d > "$tmp/rsa.der"
    openssl pkey -in "$tmp/rsa.der" -inform DER -noout >/dev/null 2>&1 || { echo "FAIL rsa parse"; return 1; }
    openssl pkey -in "$tmp/rsa.der" -inform DER -text -noout 2>/dev/null | grep -q "2048 bit" \
        || { echo "FAIL rsa size"; return 1; }
    echo "PASS generated secrets have required formats and lengths"

    python3 - "$REPO_ROOT/eladmin-system/src/main/resources/config/application.yml" \
        "$REPO_ROOT/eladmin-system/src/main/resources/config/application-dev.yml" \
        "$REPO_ROOT/eladmin-system/src/main/resources/config/application-prod.yml" <<'PY'
import sys, yaml
for path in sys.argv[1:]:
    with open(path, encoding="utf-8") as fh:
        yaml.safe_load(fh)
print("PASS shipped yml files parse as YAML")

base = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
assert base["spring"]["profiles"]["active"] == "${SPRING_PROFILES_ACTIVE:}", \
    "base yml must select the profile only from the environment"
dev = yaml.safe_load(open(sys.argv[2], encoding="utf-8"))
prod = yaml.safe_load(open(sys.argv[3], encoding="utf-8"))
assert dev["spring"]["datasource"]["druid"]["username"] == "${DB_USER:root}", \
    "dev keeps its explicit-selection convenience username default"
assert dev["spring"]["datasource"]["druid"]["password"] == "${DB_PWD:}", \
    "dev keeps its explicit-selection convenience empty password default"
d = prod["spring"]["datasource"]["druid"]
assert d["username"] == "${DB_USER}", "prod DB user must have no default"
assert d["password"] == "${DB_PWD}", "prod DB password must have no default"
assert "${DB_HOST:" not in d["url"], "prod url must not default the host"
assert "${DB_NAME:" not in d["url"], "prod url must not default the database name"
assert prod["spring"]["data"]["redis"]["password"] == "${REDIS_PWD}", \
    "prod Redis password must have no default"
assert base["amz"]["s3"]["enabled"] == "${S3_ENABLED:false}", \
    "base yml must keep S3 explicitly disabled by default (opt-in contract)"
print("PASS profile contract holds: no default profile, prod auth inputs have no fallbacks, S3 is opt-in")
PY

    local rendered_env="$tmp/local.env"
    if ! ENV_OUT="$rendered_env" DB_PWD=local-dev-password write_env self-test-jwt self-test-rsa self-test-des \
        2>/dev/null; then
        echo "FAIL local.env template could not be rendered"
        return 1
    fi
    grep -qx 'SPRING_PROFILES_ACTIVE=dev' "$rendered_env" \
        || { echo "FAIL local.env must emit the explicit dev profile contract"; return 1; }
    grep -q '^DB_PWD=.\+' "$rendered_env" \
        || { echo "FAIL local.env must carry a nonempty DB_PWD"; return 1; }
    [ "$(stat -c '%a' "$rendered_env")" = "600" ] \
        || { echo "FAIL local.env must be mode 600"; return 1; }
    echo "PASS local.env template emits the explicit profile contract"

    grep -q 'private_key: ${RSA_PRIVATE_KEY}' "$REPO_ROOT/eladmin-system/src/main/resources/config/application.yml" \
        || { echo "FAIL rsa placeholder missing"; return 1; }
    grep -q 'base64-secret: ${JWT_SECRET}' "$REPO_ROOT/eladmin-system/src/main/resources/config/application-dev.yml" \
        || { echo "FAIL jwt placeholder missing"; return 1; }
    echo "PASS shipped configuration carries placeholders, not values"

    hash="$(bcrypt_hash "$admin_pw")"
    sql_tmp="$tmp/eladmin.sql"
    sed "s|__LOCAL_ONBOARDING__|$hash|g" "$REPO_ROOT/sql/eladmin.sql" > "$sql_tmp"
    grep -q "__LOCAL_ONBOARDING__" "$sql_tmp" && { echo "FAIL seed placeholder not replaced"; return 1; }
    bcrypt_check "$admin_pw" "$hash" || { echo "FAIL bcrypt verification"; return 1; }
    python3 - "$sql_tmp" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
rendered = re.findall(r"\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}", text)
assert len(rendered) == 2, "rendered seed must carry only the generated hash on both seed rows"
assert "__LOCAL_ONBOARDING__" not in text, "rendered seed still carries the placeholder"
assert "/Users/" not in text, "rendered seed still carries a developer path"
assert re.search(r"VALUES \(2, .*, b'0', b'0',", text), "test account not disabled"
print("PASS rendered seed activates only the generated credential")
PY
    echo "PASS onboarding self-test complete"
}

real_mode() {
    local jwt rsa des admin_pw hash
    if [ -z "${DB_PWD:-}" ] && [ -t 0 ]; then
        read -s -p "Local MySQL password for ${DB_USER:-root}: " DB_PWD
        echo
    fi
    : "${DB_PWD:?DB_PWD is not set and no terminal is available for a prompt}"
    jwt="$(gen_jwt_secret)"
    rsa="$(gen_rsa_private)"
    des="$(gen_des_material)"
    admin_pw="$(gen_password 16)"
    hash="$(bcrypt_hash "$admin_pw")"

    write_env "$jwt" "$rsa" "$des"
    printf '%s' "$rsa" | base64 -d | openssl pkey -in /dev/stdin -inform DER -pubout -outform DER 2>/dev/null \
        | base64 -w0 > "$PUB_OUT"
    cat > "$SQL_OUT" <<SQL
UPDATE sys_user SET password='${hash}' WHERE user_id=${ADMIN_USER_ID};
SQL
    chmod 600 "$ENV_OUT" "$SQL_OUT" "$PUB_OUT"

    echo "local.env written (gitignored), including SPRING_PROFILES_ACTIVE=dev: the explicit local profile contract."
    echo "Load it with: set -a; . ./local.env; set +a  (then launch; a packaged launch without an explicit profile fails closed)"
    echo "Activate the seed with: mysql -u \"\$DB_USER\" -p eladmin < sql/eladmin.sql && mysql -u \"\$DB_USER\" -p eladmin < ${SQL_OUT}"
    echo "Frontend RSA public key (base64 DER, also saved to ${PUB_OUT}):"
    cat "$PUB_OUT"; echo
    echo "One-time initial admin password (user_id=${ADMIN_USER_ID}):"
    echo "$admin_pw"
}

case "${1:-}" in
    --self-test) self_test ;;
    ""|--run) real_mode ;;
    *) echo "usage: $0 [--self-test]"; exit 2 ;;
esac
