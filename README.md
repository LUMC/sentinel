# Sentinel

Sentinel is a JSON-based database for next-generation sequencing statistics.

## Requirements

- Java 8 (must be set as the default `java`)
- Scala 2.11.6
- MongoDB 3.0

## Build & Run

```sh
$ git clone {this-repository}
$ cd sentinel
$ ./sbt
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

## Support

Please report issues to [the issue page](https://git.lumc.nl/sasc/sentinel/issues). Feature suggestions are also welcome.

## Contributing

```sh
$ grep -r 'TODO' src/
```

You can also check for unclosed issues in the issue page.
