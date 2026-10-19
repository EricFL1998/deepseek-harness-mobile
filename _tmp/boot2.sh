export PROOT_LOADER=/data/app/~~DM4eubSXJcIPSfRNzd1KZQ==/com.dsh.mobile-o7e8NpNKEP4bhhww6Cq8Ng==/lib/x86_64/libproot-loader.so
export PROOT_TMP_DIR=$PWD/files/cache
rm -f $PWD/files/cache/boot.log
( /data/app/~~DM4eubSXJcIPSfRNzd1KZQ==/com.dsh.mobile-o7e8NpNKEP4bhhww6Cq8Ng==/lib/x86_64/libproot.so -r $PWD/files/rootfs -b /proc -b /dev -b /sys --link2symlink -w / -b $PWD/files/cache:/cache /usr/bin/env -i HOME=/root DSH_HOME=/root/.dsh PATH=/opt/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin NODE_ENV=production LANG=C.UTF-8 TMPDIR=/root/.dsh/tmp /usr/local/bin/node /usr/local/lib/node_modules/@deepseek-ai/dsh/lib/bin.js --profile web --no-open --port 0 > /cache/boot.log 2>&1 & )
sleep 18
echo '=== boot.log ==='
cat $PWD/files/cache/boot.log 2>/dev/null | head -30