# External Values

Trace can resolve Java values until it reaches a runtime configuration boundary. At that point the CLI needs an external dictionary.

## Dictionary Format

Use JSON:

```json
{
  "config": {
    "users.base-url": ["http://users"],
    "kafka.topic.user-created": ["user-created"],
    "redis.user-prefix": ["user:"]
  },
  "env": {
    "USER_SERVICE_URL": ["http://users"]
  }
}
```

The first level is a namespace. The second level is the key. Values are arrays because one key may have multiple resolved values.

## Where To Get Values

Prefer local project files:

- `src/main/resources/application.yml`
- `src/main/resources/application.yaml`
- `src/main/resources/application.properties`
- profile files such as `application-dev.yml`
- `.env`
- Docker Compose environment blocks
- Kubernetes ConfigMap/Secret manifests when present in the repository

Do not query databases, cloud services, or secret managers unless the user explicitly provides access and asks for it.

## How Agent Should Use It

1. Inspect local configuration files.
2. Build `.ser/generated/external-values.json`.
3. Pass it to CLI:
   ```bash
   static-extract-java run \
     --project /my-project \
     --rule /my-project/.ser/generated/http.ser \
     --external-values /my-project/.ser/generated/external-values.json \
     --out /my-project/.ser/result/extract.jsonl
   ```

The `--rule` file may include both `rule ...` and `trace ...` blocks.

## Missing Values

If a key is visible but no value is found:

- Keep the key in the final report.
- Use default value from placeholders when available, for example `${users.base-url:http://fallback}`.
- State clearly that the value was unresolved.

Do not invent values.

## Common Runtime Boundaries

These code shapes often need external values:

- Spring `@Value("${key}")`
- Spring `@ConfigurationProperties(prefix = "...")`
- `Environment.getProperty("key")`
- `System.getenv("KEY")`
- constants loaded from properties files
- framework annotations whose attributes use placeholders
