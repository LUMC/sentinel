# Sentinel

Sentinel is a JSON-based database for next-generation sequencing statistics.

## Requirements

Development requires Scala 2.11.6 and Java 8 installed. MongoDB 3.0 must also be running at localhost:27017 for some tests to pass.

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
