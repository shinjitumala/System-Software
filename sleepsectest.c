#include "types.h"
#include "stat.h"
#include "user.h"
#include "date.h"

int main(int argc, char *argv[]){
  struct rtcdate d;
  int n;
  for (n = 5; n > 0; n--){
    getdate(&d);
    printf(1, "%d:%d:%d\n", d.hour, d.minute, d.second);
    sleep_sec(10);
  }
  exit();
}
