#!/usr/bin/env bash
#
# TransitPulse - EC2 deployment helper
#
# Run ON the EC2 instance (Amazon Linux 2023 or Ubuntu 22.04+). Installs
# Docker, clones or updates the repository, and brings the stack up with
# Docker Compose.
#
#   curl -fsSL <raw-url>/aws/ec2/deploy.sh -o deploy.sh
#   chmod +x deploy.sh
#   ./deploy.sh https://github.com/<you>/TransitPulse.git
#
# No credentials are embedded. The script creates .env from .env.example and
# stops so you can fill in real values before anything starts.

set -euo pipefail

REPO_URL="${1:-}"
APP_DIR="${APP_DIR:-/opt/transitpulse}"
BRANCH="${BRANCH:-main}"

log() { printf '\n==> %s\n' "$*"; }
fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

[[ -n "$REPO_URL" ]] || fail "usage: $0 <git-repo-url>"

# ---------------------------------------------------------------- packages
log "Detecting the package manager"
if command -v dnf >/dev/null 2>&1; then
    PKG=dnf
elif command -v apt-get >/dev/null 2>&1; then
    PKG=apt-get
else
    fail "neither dnf nor apt-get found; unsupported distribution"
fi

log "Installing git and Docker with $PKG"
if [[ "$PKG" == "dnf" ]]; then
    sudo dnf -y update
    sudo dnf -y install git docker
else
    sudo apt-get update
    sudo apt-get -y install git ca-certificates curl gnupg
    if ! command -v docker >/dev/null 2>&1; then
        curl -fsSL https://get.docker.com | sudo sh
    fi
fi

log "Enabling and starting Docker"
sudo systemctl enable --now docker

# Compose v2 ships as a Docker plugin; install it if absent.
if ! docker compose version >/dev/null 2>&1; then
    log "Installing the Docker Compose plugin"
    PLUGIN_DIR=/usr/libexec/docker/cli-plugins
    sudo mkdir -p "$PLUGIN_DIR"
    ARCH="$(uname -m)"
    sudo curl -fsSL \
        "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \
        -o "$PLUGIN_DIR/docker-compose"
    sudo chmod +x "$PLUGIN_DIR/docker-compose"
fi

log "Adding $USER to the docker group"
sudo usermod -aG docker "$USER" || true

# ---------------------------------------------------------------- source
log "Preparing $APP_DIR"
sudo mkdir -p "$APP_DIR"
sudo chown "$USER":"$USER" "$APP_DIR"

if [[ -d "$APP_DIR/.git" ]]; then
    log "Updating the existing checkout"
    git -C "$APP_DIR" fetch --all --prune
    git -C "$APP_DIR" checkout "$BRANCH"
    git -C "$APP_DIR" pull --ff-only origin "$BRANCH"
else
    log "Cloning $REPO_URL"
    git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
fi

cd "$APP_DIR"

# ---------------------------------------------------------------- secrets
if [[ ! -f .env ]]; then
    log "Creating .env from .env.example"
    cp .env.example .env
    chmod 600 .env
    cat <<'MSG'

  .env has been created from the template but still holds placeholder values.

  Edit it now and set at least:
      MYSQL_ROOT_PASSWORD
      MYSQL_PASSWORD
      TRANSITPULSE_CORS_ORIGINS   (your public URL, e.g. http://<elastic-ip>:3000)

      nano .env

  Then re-run this script to start the stack.

MSG
    exit 0
fi

# Refuse to start with the shipped placeholders still in place.
if grep -q "change_me" .env; then
    fail ".env still contains placeholder passwords (change_me...). Edit it first."
fi

# ---------------------------------------------------------------- run
log "Building and starting the stack"
sg docker -c "docker compose pull --ignore-pull-failures" || true
sg docker -c "docker compose up --build -d"

log "Waiting for the backend to report healthy"
for attempt in $(seq 1 30); do
    if curl -fsS http://localhost:8080/api/health >/dev/null 2>&1; then
        log "Backend is up"
        break
    fi
    [[ $attempt -eq 30 ]] && fail "backend did not become healthy; check: docker compose logs backend"
    sleep 5
done

PUBLIC_IP="$(curl -fsS --max-time 3 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo localhost)"

cat <<EOF

==> TransitPulse is running

    Frontend   http://${PUBLIC_IP}:3000
    API        http://${PUBLIC_IP}:8080/api/health
    Swagger    http://${PUBLIC_IP}:8080/swagger-ui.html

    Logs       docker compose logs -f
    Stop       docker compose down
    Restart    docker compose restart

    Security group: allow inbound 3000 and 8080 from your own IP only.
    Port 3306 should NOT be open to the internet.

EOF
