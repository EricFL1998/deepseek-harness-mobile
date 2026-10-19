export PROOT_LOADER=/data/app/~~DM4eubSXJcIPSfRNzd1KZQ==/com.dsh.mobile-o7e8NpNKEP4bhhww6Cq8Ng==/lib/x86_64/libproot-loader.so
export PROOT_TMP_DIR=$PWD/files/cache
echo '--- resolv.conf ---'
cat $PWD/files/rootfs/etc/resolv.conf
echo '--- node dns lookup ---'
/data/app/~~DM4eubSXJcIPSfRNzd1KZQ==/com.dsh.mobile-o7e8NpNKEP4bhhww6Cq8Ng==/lib/x86_64/libproot.so -r $PWD/files/rootfs -b /proc -b /dev -b /sys --link2symlink -w / /usr/bin/env -i HOME=/root PATH=/usr/local/bin:/usr/bin:/bin TMPDIR=/root/.dsh/tmp /usr/local/bin/node -e "require('dns').lookup('github.com',(e,a)=>console.log(e? 'FAIL '+e.code+' '+e.message : 'OK '+a))"