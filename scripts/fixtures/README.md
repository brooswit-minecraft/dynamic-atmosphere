# Shared dispatch payload fixture

`dynamic-atmosphere-released.example.json` is the shared producer/consumer
contract fixture for the `dynamic-atmosphere-released` dispatch (see
`docs/release-dispatch-contract.md`). SICKOS-72's tests check against these
same bytes.

Per ATMO-15 ticket comments 22721 and 22772, these are committed verbatim
from the sickos side (built first):

- Source: `brooswit-minecraft/sickos` path
  `tests/fixtures/dynamic-atmosphere-released.example.json` at
  `main` commit `443f3219084ba6d4e77a261182add8e3aa327e80` (originally sickos
  PR #29 head `9150466b6a8e4a5fd36058f41421e86fd6fa1ee5`, since merged — same
  bytes, per ticket comment 22772). The consumer's own contract doc is
  `docs/da-auto-bump.md` in sickos.
- sha256: `ebc66b46071c104d4f92030e0b527ad170814fa3b16b322f613a5800902591d6`
  (asserted in `scripts/test_dispatch_sickos.py::FixtureTest` so drift is
  caught).

From here on, DA's copy is the source of truth and sickos checks for drift
against it — do not hand-edit this file without updating the sha256 in the
test and this note.
