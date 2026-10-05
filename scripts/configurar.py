"""Crea credenciales locales aleatorias. No modifica un .env existente."""
from pathlib import Path
import secrets
import os
root = Path(__file__).resolve().parents[1]
path = root / ".env"
if path.exists():
    print("Se conserva .env existente.")
else:
    keys = ["INFRA_PASSWORD", "DB_PASSWORD", "BROKER_PASSWORD", "BANK_CLIENT_SECRET", "READER_CLIENT_SECRET", "BFF_CLIENT_SECRET"]
    content = "OAUTH_ISSUER=http://localhost:9000\n" + "".join(k + "=" + secrets.token_hex(24) + "\n" for k in keys)
    content += "ATM_PIN=" + str(secrets.randbelow(900000) + 100000) + "\n"
    with path.open("x") as f:
        f.write(content)
    os.chmod(path, 0o600)
    print(".env creado con credenciales aleatorias; no se muestran ni se incluyen en Git.")
