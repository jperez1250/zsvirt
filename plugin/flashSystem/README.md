# IBM FlashSystem Primary Storage Plugin

This plugin connects ZStack/ZSVirt primary storage to IBM FlashSystem / Storage
Virtualize shared block storage. Its implementation has one integration path:

```text
FlashSystemPrimaryStorage -> FlashSystemApiClient -> IBM FlashSystem
                              |
                         FlashSystemKvmBackend -> FC/iSCSI multipath
```

## Layout

All implementation code lives under `org.zstack.storage.primary.flashsystem`:

- `FlashSystemPrimaryStorage` translates the primary-storage volume and snapshot
  lifecycle into array operations.
- `FlashSystemApiClient` is the only REST client. It owns authentication, token
  caching, bounded token renewal, TLS, timeouts, and request execution.
- `FlashSystemKvmBackend` handles KVM shared-block-storage integration.
- `model/` contains the array-facing vdisk, pool, and FlashCopy representations
  used by the primary-storage client; it replaces the standalone `flashsystem`
  module's duplicate DTOs and services.

The plugin deliberately does **not** expose a separate management REST controller.
Array credentials are retained in the primary-storage configuration rather than
accepted through an unauthenticated management API.

The repository contains one canonical plugin directory: `plugin/flashSystem`.
The former lowercase `plugin/flashsystem` Maven module has been folded into this
plugin rather than being built or configured independently.

## Security and configuration

The API client uses normal JVM TLS certificate and hostname validation. Install the
array CA certificate in the management node's JVM trust store before adding a
FlashSystem primary storage. The optional `restApiPort` in
`FlashSystemStorageVO` is used for every REST request; it defaults to `7443`.

Authentication uses the Storage Virtualize REST authentication headers
`X-Auth-Username` and `X-Auth-Password` against `/rest/v1/auth`. Tokens are cached
per primary storage and an authorization failure causes one, and only one, refresh
attempt.

## Operational model

1. Instantiating a ZStack volume creates a thin-provisioned array volume and maps
   it to the configured host group.
2. KVM consumes the resulting multipath device on the shared FC or iSCSI fabric.
3. Snapshots are created through the configured volume-snapshot endpoint.
4. Deletion unmaps the array volume before removing it. Snapshot source volumes are
   resolved from their multipath WWID through the array client.

Validate exact endpoint payloads and FlashCopy policy with the Storage Virtualize
version deployed on the target array before production use.
