#!/usr/bin/perl
# ============================================================================
# debuda-strip.pl — вырезает слежку и рекламу из классов Debuda (=Isle) client.
#
# Заменяет константы URL на такую же по ДЛИНЕ строку с мёртвым локальным портом
# (http://127.0.0.1:9/...). Длина сохраняется байт-в-байт, поэтому пул констант
# класса остаётся валидным и пересборка не нужна.
#
# Что нейтрализуется:
#   1. Discord-webhook #1 — IsleScreenReporter: отправка СКРИНШОТОВ экрана + uid/ник.
#   2. Discord-webhook #2 — отчёт о KillAura/"DebugChecker" с ником игрока.
#   3. https://api.telegram.org/bot — TelegramNotifier: AutoBuy-события + uid/ник.
#   4. funpay.com/lots/offer?id=64090767 + t.me/DebudaOfficial — встроенная реклама (News).
#   5. node1.yumo.su heartbeat/online — телеметрия: отправка ника на сервер автора.
#
# Использование:  perl debuda-strip.pl <каталог-с-распакованными-классами>
# ============================================================================
use strict;
use warnings;
use bytes;

my $root = shift or die "usage: perl debuda-strip.pl <extracted-client-dir>\n";

my @targets = (
    "https://discord.com/api/webhooks/1535433178859307020/qXz1YsACVAAFISvfchB_u3c-kxhZc6oA_Pdl4b4P-eIowrMXQv5X3ZcOIua1ICzBahgy",
    "https://discord.com/api/webhooks/1551643781756092526/C0jFvx9HG5HduaqgJoZsW7HrX8kZQdxQuqqbRIsmMTupmWW-4L4SgchbKDFytAdCF2Ty",
    "https://api.telegram.org/bot",
    "https://funpay.com/lots/offer?id=64090767",
    "https://t.me/DebudaOfficial",
    "http://node1.yumo.su:25566/api/heartbeat",
    "http://node1.yumo.su:25566/api/online",
);

# мёртвый адрес той же длины: локальный порт 9 (discard) — соединение всегда падает
sub dead_url {
    my ($len) = @_;
    my $base = "http://127.0.0.1:9/";
    return $base . ("a" x ($len - length($base))) if $len >= length($base);
    return substr($base, 0, $len);
}

my $total = 0;
my @changed;
for my $rel (@targets) {
    my $new = dead_url(length($rel));
    die "length mismatch for $rel\n" unless length($new) == length($rel);
    my $found = 0;
    # ищем во всех .class под $root
    my @files = `find "$root" -name '*.class' -type f`;
    chomp @files;
    for my $f (@files) {
        open(my $in, '<:raw', $f) or next;
        local $/; my $data = <$in>; close $in;
        my $n = 0;
        $n++ while $data =~ /\Q$rel\E/g;
        next unless $n;
        $data =~ s/\Q$rel\E/$new/g;
        open(my $out, '>:raw', $f) or die "write $f: $!\n";
        print $out $data; close $out;
        $found += $n;
        push @changed, "$f ($n)";
        print "  patched $f  x$n\n";
    }
    die "NOT FOUND: $rel\n" if $found == 0;
    $total += $found;
}
print "OK: заменено $total констант в " . scalar(@changed) . " местах\n";
