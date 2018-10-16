#include "date.h"
#include "defs.h"
#include "memlayout.h"
#include "mmu.h"
#include "param.h"
#include "proc.h"
#include "types.h"
#include "x86.h"

int sys_fork(void) { return fork(); }

int sys_exit(void) {
  exit();
  return 0; // not reached
}

int sys_wait(void) { return wait(); }

int sys_kill(void) {
  int pid;

  if (argint(0, &pid) < 0)
    return -1;
  return kill(pid);
}

int sys_getpid(void) { return myproc()->pid; }

int sys_sbrk(void) {
  int addr;
  int n;

  if (argint(0, &n) < 0)
    return -1;
  addr = myproc()->sz;
  if (growproc(n) < 0)
    return -1;
  return addr;
}

int sys_sleep(void) {
  int n;
  uint ticks0;

  if (argint(0, &n) < 0)
    return -1;
  acquire(&tickslock);
  ticks0 = ticks;
  while (ticks - ticks0 < n) {
    if (myproc()->killed) {
      release(&tickslock);
      return -1;
    }
    sleep(&ticks, &tickslock);
  }
  release(&tickslock);
  return 0;
}

// return how many clock tick interrupts have occurred
// since start.
int sys_uptime(void) {
  uint xticks;

  acquire(&tickslock);
  xticks = ticks;
  release(&tickslock);
  return xticks;
}

// kadai1
int sys_getdate(void) {
  struct rtcdate *dp;
  if (argptr(0, (char **)&dp, sizeof(dp)) < 0)
    return -1;
  cmostime(dp);
  return 0;
}

// kadai1
int sys_sleep_sec(void) {
  int n;
  struct rtcdate dp0, dp;

  // returns error if the int argument was negative
  if (argint(0, &n) < 0)
    return -1;

  cmostime(&dp0);
  int l = 0;
  while (1) {
    l++;
    l = l % 40;
    if (l == 0) {
      cmostime(&dp);

      int delta = 0;
      // year
      if (dp.year != dp0.year) {
        int i;
        for (i = dp0.year; i != dp.year; i++) {
          if (i % 4 != 0 || (i % 4 == 0 && i % 100 == 0 && i % 400 != 0)) {
            delta += 31536000;
          } else {
            delta += 31622400;
          }
        }
      }
      // month
      if (dp.month != dp0.month) {
        int year = dp0.year;
        int i;
        for (i = dp0.month; i != dp.month;) {
          switch (i) {
          case 1:
          case 3:
          case 5:
          case 7:
          case 8:
          case 10:
          case 12:
            delta += 2678400;
            break;
          case 4:
          case 6:
          case 9:
          case 11:
            delta += 2592000;
            break;
          case 2:
            if (year % 4 != 0 ||
                (year % 4 == 0 && year % 100 == 0 && year % 400 != 0)) {
              delta += 2419200;
            } else {
              delta += 2505600;
            }
            break;
          }

          if (i == 12) {
            i = 1;
            year = dp.year;
          } else
            i++;
        }
      }
      // day
      if (dp0.day != dp.day) {
        int year = dp0.year;
        int month = dp0.month;
        int i;
        for (i = dp0.day; i != dp.day;) {
          delta += 86400;

          int max = 0;
          switch (month) {
          case 1:
          case 3:
          case 5:
          case 7:
          case 8:
          case 10:
          case 12:
            max = 31;
            break;
          case 4:
          case 6:
          case 9:
          case 11:
            max = 30;
            break;
          case 2:
            if (year % 4 != 0 ||
                (year % 4 == 0 && year % 100 == 0 && year % 400 != 0)) {
              max = 28;
            } else {
              max = 29;
            }
            break;
          }

          if (i == max) {
            year = dp.year;
            month = dp.month;
            i = 1;
          } else {
            i++;
          }
        }
      }
      // hour, minute, second
      delta += (dp.second - dp0.second) + (dp.minute - dp0.minute) * 60 +
               (dp.hour - dp0.hour) * 3600;
      if (delta >= n)
        break;
    }
  }

  return 0;
}
