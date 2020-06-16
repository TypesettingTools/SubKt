[subkt](../../index.md) / [myaa.subkt.tasks](../index.md) / [SubProperties](./index.md)

# SubProperties

`class SubProperties`

Reads a list of properties of the form `release.entry.property=value`, which can
later be looked up using the [match](match.md) method.

Properties may contain wildcards, which will match any sequence of characters.
For instance, a property defined as `TV.episode*.name=value` will be found
with a `match("name", entry="episode01", release="TV")` call.

Unspecified release and entry components are equivalent to wildcards.
In other words, `name=value` is equivalent to `*.*.name=value`, while
`01.name=value` is equivalent to `*.01.name=value`.

Properties are ordered, and the value of the last property matching the
[match](match.md) query will be returned. Thus, later values will override earlier values.

Lines starting with `#` will be interpreted as comments and ignored.

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `SubProperties()`<br>Reads a list of properties of the form `release.entry.property=value`, which can later be looked up using the [match](match.md) method. |

### Functions

| Name | Summary |
|---|---|
| [match](match.md) | `fun match(property: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, entry: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)` = "", release: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)` = ""): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`?`<br>Finds the last property entry that matches the given property, entry and release. |
| [parse](parse.md) | `fun parse(f: `[`File`](https://docs.oracle.com/javase/9/docs/api/java/io/File.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |