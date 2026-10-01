# PresentMon CSV fixture

`presenter.csv` contains the 18 rows for Presenter.exe (PID 10016) from the official
PresentMon v2.6.0 `Tests/Gold/test_case_2.etl`, replayed with the pinned v2.6.0 x64 executable:

```
PresentMon-2.6.0-x64.exe --etl_file test_case_2.etl --output_file replay.csv --no_console_stats --no_track_input --no_track_gpu --track_frame_type
```

Source: https://github.com/GameTechDev/PresentMon/tree/v2.6.0/Tests/Gold

These records include a dropped frame (`NA` display interval). Their mean present
interval gives 69 FPS and their mean available display interval gives 60 FPS,
rounded to integers. The fixture also verifies the actual `TimeInMs` header.

Copyright (C) 2017-2024 Intel Corporation. MIT license; see
`src/main/resources/presentmon/LICENSE.txt` in this repository.
