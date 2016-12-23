/* Code to get discid for a cddb query.

  *** Linux Version ***

  $Id: discid.c,v 1.3 2001/04/14 17:54:53 jzawodn Exp $

  Copyright (c) 1998-2000 Jeremy D. Zawodny <Jeremy@Zawodny.com>

  This software is covered by the GPL.

  Is is based on code found in:

    To: code-review@azure.humbug.org.au 
    Subject: CDDB database reader 
    From: Byron Ellacott <rodent@route-qn.uqnga.org.au> 
    Date: Fri, 5 Jun 1998 17:32:40 +1000 

*/

/* Stripped net code, 'cause I only care about the discid */

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <stdarg.h>
#include <errno.h>
#include <netdb.h>
#include <unistd.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>

#include <linux/cdrom.h>

struct toc {
    int min, sec, frame;
} cdtoc[100];

int read_toc(void) {
    int drive = open("/dev/cdrom", O_RDONLY | O_NONBLOCK);
    struct cdrom_tochdr tochdr;
    struct cdrom_tocentry tocentry;
    int i;

    ioctl(drive, CDROMREADTOCHDR, &tochdr);
    for (i = tochdr.cdth_trk0; i <= tochdr.cdth_trk1; i++) {
        tocentry.cdte_track = i;
        tocentry.cdte_format = CDROM_MSF;
        ioctl(drive, CDROMREADTOCENTRY, &tocentry);
        cdtoc[i-1].min = tocentry.cdte_addr.msf.minute;
        cdtoc[i-1].sec = tocentry.cdte_addr.msf.second;
        cdtoc[i-1].frame = tocentry.cdte_addr.msf.frame;
        cdtoc[i-1].frame += cdtoc[i-1].min*60*75;
        cdtoc[i-1].frame += cdtoc[i-1].sec*75;
    }
    tocentry.cdte_track = 0xAA;
    tocentry.cdte_format = CDROM_MSF;
    ioctl(drive, CDROMREADTOCENTRY, &tocentry);
    cdtoc[tochdr.cdth_trk1].min = tocentry.cdte_addr.msf.minute;
    cdtoc[tochdr.cdth_trk1].sec = tocentry.cdte_addr.msf.second;
    cdtoc[tochdr.cdth_trk1].frame = tocentry.cdte_addr.msf.frame;
    cdtoc[tochdr.cdth_trk1].frame += cdtoc[tochdr.cdth_trk1].min*60*75;
    cdtoc[tochdr.cdth_trk1].frame += cdtoc[tochdr.cdth_trk1].sec*75;
    close(drive);
    return tochdr.cdth_trk1;
}

unsigned int cddb_sum(int n) {
    unsigned int ret;

    ret = 0;
    while (n > 0) {
      ret += (n % 10);
      n /= 10;
    }
    return ret;
}

unsigned long cddb_discid(int tot_trks) {
    unsigned int i, t = 0, n = 0;

    i = 0;
    while (i < tot_trks) {
      n = n + cddb_sum((cdtoc[i].min * 60) + cdtoc[i].sec);
      i++;
    }
    t = ((cdtoc[tot_trks].min * 60) + cdtoc[tot_trks].sec) -
      ((cdtoc[0].min * 60) + cdtoc[0].sec);
    return ((n % 0xff) << 24 | t << 8 | tot_trks);
}

void main(void) {
    unsigned long discid;
    int tracks, i;

    tracks = read_toc();
    discid = cddb_discid(tracks);
    printf("%08x %d", discid, tracks);
    for (i = 0; i < tracks; i++) printf(" %d", cdtoc[i].frame);
    printf(" %d\n", (cdtoc[tracks].frame)/75);
    printf("\n");
    exit(0);
}



