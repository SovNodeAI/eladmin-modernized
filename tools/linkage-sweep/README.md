# runtime linkage sweep

A dependency-free, single-file Java 21 tool that answers the question the
eladmin email incident made concrete: does any jar on the resolved
runtime classpath still reference a namespace that was supposed to be
gone?

## Why this exists

The critical mail defect shipped because the migration swapped the
javax-namespace mail provider for angus-mail while first-party code still
called hutool's `cn.hutool.extra.mail.Mail`, whose bytecode is compiled
against `javax/mail`. Everything compiled cleanly; the first send threw
`NoClassDefFoundError: javax/mail/Multipart` from inside
`EmailServiceImpl.send`. No source-level import grep could see that
reference, because it lived in a third-party jar's constant pool.

Rule: when migrating a namespace, enumerate every library whose bytecode
references the old namespace, not just first-party imports. Compile
success does not prove link success.

## Usage

From the repository root, first materialize the runtime classpath:

```
mvn -B -pl eladmin-system dependency:build-classpath \
    -Dmdep.outputFile=target/runtime-classpath.txt -Dmdep.includeScope=runtime
```

Then sweep for the removed namespace:

```
java tools/linkage-sweep/LinkageSweep.java javax/mail \
    $(cat eladmin-system/target/runtime-classpath.txt | tr ':' ' ')
```

Output lists every artifact that still references the namespace and the
number of referencing class files. Exit code 0 means the sweep ran;
offenders are findings to triage, not automatically failures, because a
referencing third-party jar is only a defect when first-party code
actually calls that path.

## Triage rule

For each artifact the sweep reports, one of the following must hold:

1. No first-party code calls the offending path (the reference is dead
   weight in the library; in eladmin this is the case for hutool's
   `Mail`/`InternalMailUtil` classes after the fix: the service uses
   `JakartaMail` only).
2. A provider for the namespace is still on the runtime classpath (then
   the namespace was not actually removed and the sweep needle is
   wrong).
3. The call path is exercised by a behavioral test that would fail with
   `NoClassDefFoundError` (the mail test does exactly this).

Anything else is an unfixed linkage break.

## Verified result on this branch

Sweep executed against the eladmin-system runtime classpath resolved
from a clean detached build of commit 0cdc14d (259 jars) for
`javax/mail`: exactly one artifact still references the namespace,
hutool-all 5.8.35 with 4 referencing class files (`Mail`, `MailUtil`,
`InternalMailUtil`, and the attachment helper), and no jar on the
classpath provides the javax.mail classes. An earlier run of this
delta reported 182 jars; that number came from a stale classpath
snapshot left over from mid delta dependency resolution and is
superseded. Conditions 1 and 3 from the triage rule hold for the
offending artifact: first-party code calls only hutool's jakarta
variants, and `EmailServiceImplBehaviorTest` fails on any regression
with `NoClassDefFoundError: javax/mail/Multipart`.

The jar count is not itself the claim. It tracks whatever the
resolved runtime classpath contains on the day it is regenerated and
will move with dependency changes; the load bearing invariant is that
hutool-all is the only referencing artifact with the same four class
files and that no provider ships the namespace. Raw outputs are
archived with the run evidence as `16-linkage-sweep-javax-mail.txt`
(stale snapshot), `19-linkage-sweep-recheck.txt` (clean reproduction,
259 jars), and the independent verifier's
`independent-final-linkage-sweep.txt` (also 259 jars).
