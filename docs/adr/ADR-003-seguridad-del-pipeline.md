# ADR-003. Controles de seguridad del pipeline

**Estado:** aceptada

## Contexto

El pipeline debe detenerse cuando aparece un problema de seguridad conocido: vulnerabilidades en el código propio, en las dependencias o en la imagen, y secretos en el repositorio o en su historial. Los umbrales mínimos los fija el enunciado del proyecto; el equipo puede endurecerlos, no relajarlos.

## Decisión

| Control | Herramienta | Dónde corre | Umbral (detiene el pipeline cuando...) |
|---|---|---|---|
| SAST | CodeQL (`github/codeql-action@v4`), suite por defecto | job `sast` de `ci.yml` + `scripts/umbral-sast.sh` | hay un hallazgo con `security-severity` ≥ 7.0 (alto o crítico) que no está registrado como tratado |
| SCA | Trivy 0.76.0 (`trivy fs` sobre el `pom.xml`) + Dependabot | job `sca` + `scripts/sca.sh` | hay una vulnerabilidad HIGH o CRITICAL con corrección disponible |
| Secretos | Gitleaks 8.30.1 sobre todo el historial | job `secretos` + `scripts/secretos.sh` | encuentra cualquier secreto |
| Imagen | Trivy 0.76.0 (`trivy image`) | job `imagen` y job `publicar` + `scripts/escaneo-imagen.sh` | hay una vulnerabilidad CRITICAL con corrección disponible |
| Cadena de suministro: SBOM | Trivy 0.76.0 (`--format cyclonedx`) | job `imagen` y job `publicar` + `scripts/sbom.sh` | no detiene el pipeline: se publica como artefacto y se adjunta a la imagen como atestación |
| Cadena de suministro: firma | cosign 3.1.3, firma sin llaves (Sigstore) con el token OIDC de GitHub | `publicar` firma el digest; `desplegar` y `revertir` verifican con `scripts/verificar-firma.sh` | la imagen no tiene una firma hecha por `cd.yml` de este repositorio desde `main` o desde un tag `vX.Y.Z`: no se despliega |

Reglas comunes:

- Las herramientas se ejecutan con imágenes Docker de versión fija, desde scripts que el equipo también puede correr en su máquina. No se usan actions de terceros para Trivy ni Gitleaks.
- Cada excepción (`.trivyignore`, `.github/codeql/excepciones-sast.txt`) debe tener su registro en `docs/seguridad.md` como falso positivo o riesgo aceptado.
- Los reportes completos se publican como artefactos de cada ejecución.
- La imagen que se publica en GHCR es la misma que se escaneó en el job `publicar`.

## Alternativas consideradas

| Alternativa | Por qué no se eligió |
|---|---|
| Semgrep o SonarCloud para SAST | Válidas. CodeQL es gratuito en repositorios públicos, se integra con la pestaña Security y no requiere cuentas externas. |
| OWASP Dependency-Check para SCA | Necesita descargar la base de datos NVD (lento sin una API key). Trivy usa su propia base y sirve también para la imagen. |
| `aquasecurity/trivy-action` | Funciona, pero agrega una action de terceros al pipeline. Un comando `docker run` con versión fija es más fácil de auditar y de reproducir en local. |
| TruffleHog para secretos | Válida. Gitleaks es más simple de configurar y de correr en local. |
| Solo Dependabot como SCA | Dependabot abre alertas y Pull Requests, pero no detiene el pipeline. Por eso se usa junto con Trivy. |
| Firmar con un par de llaves propio (`cosign generate-key-pair`) | Funciona también en repositorios privados, pero obliga a guardar, rotar y proteger una llave privada. La firma sin llaves no tiene secreto que filtrar: la identidad la da el workflow. |
| Atestaciones de artefactos de GitHub (`actions/attest-build-provenance`) | Válidas y también usan Sigstore. Se verifican con `gh attestation verify`, que exige la CLI de GitHub en el runner. cosign funciona con cualquier registro y se puede ejecutar como contenedor. |
| Docker Content Trust (Notary v1) | Ya no se mantiene y GHCR no lo soporta. |
| Generar el SBOM con Syft o con el plugin de CycloneDX para Maven | Válidas. Trivy ya está en el pipeline y su SBOM incluye los paquetes del sistema operativo de la imagen, no solo las librerías Java. |

## Consecuencias

- CodeQL no detiene el pipeline por sí solo: el paso `scripts/umbral-sast.sh` lee el SARIF y aplica el umbral. Como complemento se puede activar la regla *Require code scanning results* en el ruleset de `main`.
- Trivy necesita resolver las dependencias del `pom.xml`. Para no depender de Maven Central (que limita las peticiones repetidas), el script descarga primero las dependencias con Maven y le pasa a Trivy la caché local `~/.m2`.
- Los umbrales de la imagen (solo CRITICAL) son menos estrictos que los de SCA (HIGH y CRITICAL) porque el sistema base tiene vulnerabilidades sin corrección que el equipo no controla. Se revisan en el reporte completo.
- Las versiones de Trivy y Gitleaks quedan fijas en los scripts. Actualizarlas es un cambio que pasa por Pull Request como cualquier otro.
- La firma sin llaves queda en Rekor, un registro de transparencia público. En un repositorio privado el registro revelaría su nombre, por eso allí no se firma (H-08 en `docs/seguridad.md`).
- Se despliega por digest (`imagen:tag@sha256:...`): Docker usa el digest que se verificó, aunque alguien mueva la etiqueta en el registro después de la verificación.
- El job `publicar` necesita el permiso `id-token: write` para pedir el certificado de firma. Ningún otro job lo tiene.
