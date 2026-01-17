# Android GUI for [WireGuard](https://www.wireguard.com/) with DoH and IP4P

This is an Android GUI for [WireGuard](https://www.wireguard.com/). It [opportunistically uses the kernel implementation](https://git.zx2c4.com/android_kernel_wireguard/about/), and falls back to using the non-root [userspace implementation](https://git.zx2c4.com/wireguard-go/about/).

Add DoH and [IP4P](https://github.com/heiher/natmap/issues/9) support.

[Origin Repo](https://github.com/WireGuard/wireguard-android)

## Building

```
$ git clone --recurse-submodules <repo-url>
$ cd wireguard-android
$ ./gradlew assembleRelease
```

macOS users may need [flock(1)](https://github.com/discoteq/flock).
