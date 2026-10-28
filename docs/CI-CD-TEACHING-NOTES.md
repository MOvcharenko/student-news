# CI/CD teaching notes — Week 10

Notes for running the Week 10 live demo and classwork against this repository.
Everything below refers to real files here; nothing is hypothetical.

## What this repo now has

Before: one workflow, `e2e.yml`, running Playwright against the full Docker
Compose stack. Thorough, but slow, and it was the *only* automated check.

Now there are two workflows, and the split is the lesson:

| Workflow | Runs | Takes | Catches |
|---|---|---|---|
| `ci.yml` | every push and PR | ~1–3 min | compile errors, failing unit tests, broken frontend build, broken Dockerfile |
| `e2e.yml` | every push and PR | ~10–20 min | the things only a running system reveals |

`ci.yml` has four jobs arranged in two stages plus a summary:

```
backend-test ─┐
              ├─→ package (matrix: backend, frontend) ─→ ci-status
frontend-build ┘
```

Stage one is cheap and parallel. Stage two only starts if stage one is green,
because building container images for code whose tests already failed wastes
several minutes per run.

## Mapping to the slides

- **Build** → `mvn -B test` compiles; `npm run build` produces `frontend/dist`
- **Test** → `NewsServiceTest` (Mockito, no database) runs in seconds
- **Package** → `docker/build-push-action` builds both images, `push: false`
- **Deploy** → deliberately absent. That is the Final Project.

The integration tests are excluded from `ci.yml` with
`-Dtest='!*IntegrationTest,!*E2ETest'` because they need PostgreSQL. They run
in `e2e.yml`, which brings up the Compose stack. Worth saying out loud: the same
test suite is split across two pipelines on the basis of *what each test needs*,
not on where the files happen to live.

## Live demo — red, then green (~20 min)

1. **Show the two workflow files.** Read `ci.yml` aloud: `on`, `jobs`, `needs`,
   `runs-on`, `steps`. Point at `concurrency` and explain why a superseded run
   is cancelled.

2. **Push a trivial commit** (a README typo) and open the Actions tab. Both
   workflows start. `ci.yml` finishes while `e2e.yml` is still installing
   browsers — that contrast is the whole argument for stage ordering.

3. **Break a unit test.** In
   `backend/src/test/java/com/example/studentnews/service/NewsServiceTest.java`,
   change an expected value — for example an assertion on the returned title.
   Commit on a branch and open a PR.

4. **Watch it go red.** Open the failed job, expand the `Run unit tests` step,
   and scroll to the Surefire output. Show the actual assertion message rather
   than the red X on the summary page. Then show that `package` never ran: the
   `needs:` line stopped it.

5. **Download the artifact.** `backend-surefire-reports` is attached even on
   failure. Open the XML and show the same message. The point: a CI failure
   leaves evidence you can inspect after the runner is gone.

6. **Fix and push.** Watch the PR check flip to green and `package` start.

If runners are queued, fall back to a completed run from an earlier session —
reading the log is the skill, waiting is not.

## Classwork — Break the Pipeline (~45 min, individual)

Students work in their own fork.

1. Fork this repository and confirm `CI` passes on `main`.
2. On a branch, break something **on purpose** — pick one:
   - an assertion in `NewsServiceTest`
   - a syntax error in `frontend/src/App.jsx`
   - an invalid instruction in `backend/Dockerfile`
3. Open a PR and watch which job fails. Note *which stage* caught it and how
   long it took.
4. Find the exact line in the log that names the cause. Screenshot it.
5. Fix, push, confirm green.
6. Answer: your break was caught at one stage — if that stage did not exist,
   which later stage would have caught it, and how much slower would that have been?

The third option is the interesting one: a broken Dockerfile passes every test
and only fails at `package`, which is exactly why packaging belongs in CI rather
than at deploy time.

## Things that will come up

- **`npm ci` vs `npm install`.** `ci.yml` uses `npm ci`, which installs exactly
  what `package-lock.json` pins. The lock file was added for this — without it
  `npm ci` fails outright. This is a good two-minute aside on reproducible builds.
- **Caching.** `cache: maven` and `cache: npm` turn a three-minute dependency
  download into seconds. Show a cold run against a warm one if you have both.
- **`fail-fast: false`** on the package matrix means a broken backend image
  still lets you see whether the frontend image builds. Default behaviour would
  cancel the sibling job.
- **`ci-status`** exists so branch protection can require one check instead of
  four. Mention it only if someone asks about protected branches.
