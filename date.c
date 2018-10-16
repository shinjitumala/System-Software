#include "date.h"
#include "stat.h"
#include "types.h"
#include "user.h"

int main(void) {
  struct rtcdate d;
  if (getdate(&d) < 0)
    printf(2, "Error: getdate failed\n");
  else
    printf(1, "%d:%d:%d %d/%d/%d\n", d.hour, d.minute, d.second, d.month, d.day,
           d.year);
  exit();
}
