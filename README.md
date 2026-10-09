# RestClient

**A desktop and command line client for REST web services: send HTTP requests, inspect responses and run simple load tests.**

RestClient combines a Swing GUI for exploring and testing REST services with a curl-like command line mode for scripts. Requests can be saved as presets, exported as YAML files and executed in parallel by multiple workers.

---

## Features

- **GUI and command line**: build requests interactively or run them from scripts with the same presets and files
- **Full request control**: HTTP method, URL parameters, request headers, HTML form parameters and request body (typed or from a file)
- **Readable responses**: status code, response time, IP address and headers, with pretty-printed JSON
- **Response data path**: show only a part of the response via JsonPath (JSON, YAML) or XPath (XML)
- **Authentication**: Basic Auth, Bearer token and OAuth client credentials token from an IdP (e.g. Keycloak)
- **TLS validation options**: system TrustStore, own TrustStore or PEM certificate, recording of server certificates, or no check
- **Proxy support**: direct connection, explicit proxy or WPAD autodetection
- **Redirects**: optionally follow redirects up to a configurable number of hops
- **File downloads**: save attachment responses to a directory or file
- **OpenAPI**: load an OpenAPI document from a URL or file and fill the request from one of its service methods
- **Presets**: save, load and delete complete requests; also usable on the command line
- **Export and import**: store requests and responses as YAML files, or requests as cURL commands
- **Multiple request tests**: run requests with parallel workers, repetitions, sleep and ramp-up time, and see success and error counts and min/average/max durations
- **Random parameters**: placeholders like `${rnd:UUID}` for generated values in URLs, headers and bodies
- **Auto update**: check for and install new versions from within the application

## Requirements

- Java 17 or newer

## Getting started

```bash
# Open the GUI (also the default when started without parameters)
java -jar RestClient.jar

# Send a request from the command line
java -jar RestClient.jar --url https://example.com/api/items

# Show the help
java -jar RestClient.jar help
```

## The GUI

The main window shows the request on the left and the response on the right.

- **Request**: choose the HTTP method (`GET`, `HEAD`, `POST`, `PUT`, `DELETE`), enter the service URL and add URL parameters, headers (with shortcuts for `Content-Type` and standard headers), HTML form parameters and the request body.
- **OpenAPI**: load the OpenAPI document of the service (from a URL or local file) and select a service method to fill the request.
- **Connection**: configure the proxy, the TLS validation and redirect handling.
- **Authentication**: add a Basic Auth or token header, or fetch a client token from an IdP. The IdP credentials can optionally be stored.
- **Response**: status code, response time, IP address, headers and the rendered body. Edit the data path to narrow down the shown content.
- **Presets**: save the current request under a name and load it again later.
- **Export / Import**: write the request and response to a YAML file, or the request as cURL command, and import both formats again.
- **Multiple request tests**: start parallel workers to repeat the request, e.g. as a simple load test, and save the results.

## Command line

Without `gui`, RestClient executes a single HTTP request and prints the response body to stdout.

```
java -jar RestClient.jar --url <url> [options...]
java -jar RestClient.jar --preset <name> [options...]
java -jar RestClient.jar --request-file <path> [options...]
java -jar RestClient.jar --list-presets
```

One of `--url`, `--preset` or `--request-file` is required. A preset or request file is loaded first and every other option then overrides its values (or, for `--header`, `--url-param` and `--form-param`, adds to them). This works the same way as loading a preset in the GUI and changing single fields afterwards.

### Examples

```bash
# POST a JSON body and fail on HTTP errors
java -jar RestClient.jar --url https://example.com/api/items --method POST \
  --header "Content-Type: application/json" --body '{"name": "Test"}' --fail

# Print only one value of the JSON response
java -jar RestClient.jar --url https://example.com/api/items --response-data-path '$.data.items[0].id'

# Use a saved preset with an additional header and write the response to a file
java -jar RestClient.jar --preset "Get items" --header "X-Trace: 42" --output items.json

# Fetch an OAuth token from an IdP first and use it as Bearer token
java -jar RestClient.jar --url https://example.com/api/secure \
  --idp-url https://idp.example.com --idp-realm myrealm --idp-username my-client --idp-password secret

# Trust only a given server certificate
java -jar RestClient.jar --url https://internal.example.com/api \
  --tls-check-type SingleCertificate --tls-check-file server.pem
```

### Request

| Parameter | Description |
|---|---|
| `--url <url>` | Full request URL (required unless `--preset` or `--request-file` is used) |
| `--method <method>` | `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `TRACE` (default: `GET`) |
| `--header <name: value>` | Adds an HTTP request header. Repeatable. |
| `--url-param <name=value>` | Adds a URL (query string) parameter. Repeatable. |
| `--form-param <name=value>` | Adds an HTML form parameter (`application/x-www-form-urlencoded`). Repeatable. Form parameters are sent as the request body for every method and take precedence over `--body`. With `GET`, Java's HttpURLConnection then sends the request as `POST`. |
| `--body <text>` | Request body. Only used for `POST` and `PUT`, and only without `--form-param`. |
| `--body-file <path>` | Reads the request body from a local text file instead of `--body` |

### Presets and request files

| Parameter | Description |
|---|---|
| `--preset <name>` | Loads a request preset saved in the GUI |
| `--request-file <path>` | Loads a request (including download target and response data path) from a YAML file exported by the GUI. Cannot be combined with `--preset`. |
| `--list-presets` | Prints the names of all saved presets and exits |

### Connection

| Parameter | Description |
|---|---|
| `--proxy <DIRECT\|WPAD\|host:port>` | `DIRECT` disables any proxy, `WPAD` autodetects one |
| `--max-redirects <n>` | Maximum number of redirects to follow (default: 0, no redirects) |
| `--tls-check-type <type>` | TLS validation, see below (default: `SystemTrustStore`) |
| `--tls-check-file <path>` | TrustStore or PEM file (needed by most types other than `SystemTrustStore` and `NoCheck`) |
| `--tls-check-password <password>` | TrustStore password (only for the TrustStore file based types) |
| `--tls-check-cn <true\|false>` | Also check the certificate's common name/SAN against the host name |

TLS validation types:

| Type | Description |
|---|---|
| `SystemTrustStore` | *(Default)* Trust the certificates of the Java system TrustStore |
| `TrustStoreFile` | Trust only the certificates of a TrustStore file |
| `AdditionalTrustStoreFile` | Trust the system TrustStore plus the certificates of a TrustStore file |
| `RecordingToTrustStoreFile` | Record the server certificates into a TrustStore file |
| `SingleCertificate` | Trust only a single certificate (PEM file) |
| `RecordingSingleCertificate` | Record the server certificate into a PEM file |
| `NoCheck` | No certificate validation (for tests only) |

### Authentication

| Parameter | Description |
|---|---|
| `--basic-auth <username:password>` | Adds an `Authorization: Basic ...` header |
| `--bearer-token <token>` | Adds an `Authorization: Bearer ...` header |
| `--idp-url <url>` | IdP/OAuth base URL or full token endpoint URL (ending in `/token`). RestClient first requests a client credentials token and adds it as `Authorization: Bearer ...` header. |
| `--idp-realm <realm>` | Realm used to discover the token endpoint (ignored if `--idp-url` ends in `/token`) |
| `--idp-username <username>` | Client ID for the token request |
| `--idp-password <password>` | Client secret for the token request |

### Response and output

| Parameter | Description |
|---|---|
| `--response-data-path <path>` | Prints only a part of the response: JsonPath for JSON (e.g. `$.data.items[0].id`), the same syntax for YAML, XPath for XML (e.g. `//item[1]/@id`) |
| `--download-target <path>` | Directory or file to save a file download (`Content-Disposition: attachment`) to |
| `--output <path>` | Writes the (rendered) response body to a file instead of stdout |
| `-v`, `--verbose` | Also prints the HTTP status code and the response headers to stderr |
| `--fail` | Exit with a non-zero exit code if the HTTP status code is 400 or higher |

**Exit code:** `0` once a response was received, regardless of its HTTP status code (like curl). Only network, parameter or parsing errors result in a non-zero exit code, unless `--fail` is given.

### Global parameters

These are only valid on their own, not combined with request parameters:

| Parameter | Description |
|---|---|
| `help` | Show the help |
| `version` | Show the installed version |
| `gui` | Open the GUI (also the default without parameters, unless the environment is headless) |
| `update [username [password]]` | Check for an online update and install it after confirmation |

## Random parameter values

Placeholders are replaced by generated values when a request is sent:

```
${rnd:<ParameterType>[:<OptionalSlotID>[:<OptionalDescriptor>]]}
```

| Type | Example | Result |
|---|---|---|
| `UUID` | `${rnd:UUID}` | Random UUID |
| `INT` | `${rnd:INT}` | Random integer |
| `INT` with limits | `${rnd:INT:1:1-100}` | Random integer from 1 to 100, slot 1 |
| `STR` | `${rnd:STR:1:8}` | Random string with 8 characters, slot 1 |
| `TS` | `${rnd:TS}` | Current Unix timestamp in milliseconds |
| `ISO` | `${rnd:ISO}` | Current timestamp in ISO 8601 format |
| `HEX` | `${rnd:HEX:1:32}` | Random hex string with optional length, slot 1 |
| `BOOL` | `${rnd:BOOL}` | Randomly `true` or `false` |

The slot ID connects placeholders: all occurrences of the same type with the same slot ID get exactly the same value, e.g. to use one generated ID in the URL and in the body.

## Configuration files

| File | Content |
|---|---|
| `~/.RestClient.config` | Application configuration |
| `~/.RestClient/RequestPresets.json` | Request presets saved in the GUI (also used by `--preset` and `--list-presets`) |

## Disclaimer

RestClient is a utility tool for sending REST requests. It is intended for the experimental processing of data, and any use is at your own risk. The developer assumes **no warranty**, neither for correct functionality nor for damages resulting from the use of the program.

## Feedback

Please send suggestions for improvements or bug reports to [restclient@soderer.de](mailto:restclient@soderer.de).
