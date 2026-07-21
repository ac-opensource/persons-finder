# Persons Finder Help

Start with the [project README](README.md) for prerequisites, quick start,
manual API examples, dashboard usage, verification, and the isolated benchmark
workflow.

Run the complete core verification, which makes no live-model call, with:

```bash
./scripts/verify.sh
```

The default stack uses the deterministic bio generator and requires no model
API key. Additional project references:

- [Security boundary and production gaps](SECURITY.md)
- [Requirements and decision traceability](docs/REQUIREMENTS_TRACEABILITY.md)
- [Explicit opt-in live-model evaluation](docs/LIVE_AI_EVALUATION.md)
- [Curated AI collaboration record](AI_LOG.md)
