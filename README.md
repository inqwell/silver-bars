# silver bars

An order manager demo

## Prerequisites

This project was built with [Leiningen][] 2.7.1. You can download `lein` or `lein.bat` from [leiningen.org][], place it on your $PATH and allow it to self-install. However if this doesn't work behind your firewall try the windows installer. For all platforms you can place Leiningen's jar file `leiningen-2.7.1-standalone.jar` at `~/.lein/self-installs`. Specifically:

Win 7: `C:\Users\<username>\.lein\self-installs`

Win XP: `C:\Documents and Settings\<username>\.lein\self-installs`

*nix: `~/.lein/self-installs`

To make it possible to retrieve the dependencies you might need to set an environment variable thus:

    http_proxy=<user>:<passwd>@<proxy_host>:<proxy_port>

using your LDAP credentials commonplace inside organisations.

[leiningen]: https://github.com/technomancy/leiningen
[leiningen.org]: http://leiningen.org

## Running

Run the tests using

    lein test
    
or you can see the order manager in action using any REPL environment

    lein repl

## Api Documents

Produce these using

    lein codox

and they will appear in the target directory

## License

Copyright © 2016 Tom



