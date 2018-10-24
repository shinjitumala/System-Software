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

  // calculate the rtcdate after n seconds
  dp0.second += n;
  if (dp0.second >= 60) {
    dp0.minute += dp0.second / 60;
    dp0.second %= 60;
    if (dp0.minute >= 60) {
      dp0.hour += dp0.minute / 60;
      dp0.minute %= 60;
      if (dp0.hour >= 24) {
        dp0.day = dp0.hour / 24;
        dp0.hour %= 24;
        int day_remaining = 1;

        // day, month, year
        while (day_remaining != 0) {
          switch (dp0.month) {
          case 2:
            if (dp0.year % 4 != 0 ||
                (dp0.year % 4 == 0 && dp0.year % 100 == 0 &&
                 dp0.year % 400 != 0)) {
              if (dp0.day > 28) {
                dp0.day -= 28;
                dp0.month++;
              } else {
                day_remaining = 0;
              }
            } else {
              if (dp0.day > 29) {
                dp0.day -= 29;
                dp0.month++;
              } else {
                day_remaining = 0;
              }
            }
            break;
          case 1:
          case 3:
          case 5:
          case 7:
          case 8:
          case 10:
          case 12:
            if (dp0.day > 31) {
              dp0.day -= 31;
              dp0.month++;
            } else {
              day_remaining = 0;
            }
            break;
          case 4:
          case 6:
          case 9:
          case 11:
            if (dp0.day > 30) {
              dp.day -= 30;
              dp.month++;
            } else {
              day_remaining = 0;
            }
            break;
          }
        }
      }
    }
  }

  int l = 0;
  // sleep until the current rtcdate dp is ahead of dp0
  acquire(&tickslock);
  while (1) {
    if (myproc()->killed) {
      release(&tickslock);
      return -1;
    }

    l++;
    l = l % 40;
    if (l == 0) { // only call costime every 40 ticks to save resource
      cmostime(&dp);
      if (dp.second >= dp0.second && dp.minute >= dp0.minute &&
          dp.hour >= dp0.hour && dp.day >= dp0.day && dp.month >= dp0.month &&
          dp.year >= dp0.year) {
        break;
      }
    }
    sleep(&ticks, &tickslock);
  }
  release(&tickslock);
  
  return 0;
}
