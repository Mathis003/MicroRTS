# 2026 MicroRTS LLM Game AI Competition

* This competition is part of [2026 IEEE WCCI](https://attend.ieee.org/wcci-2026/competitions/). 

### Getting Started with MicroRTS LLM-Based Game AI Agents

* Clone out https://github.com/drchangliu/MicroRTS
* Ensure that you have your own local Ollama installation with the `llama3.1:8b` model. Run command `%ollama run llama3.1:8b` and leave it running in a Terminal.
* On the same computer, follow the [instructions](https://github.com/drchangliu/MicroRTS/) (the `Run Multiple Games Automatically` section) to run a minimal benchmark to gain a set of benchmark numbers as an exercise
```
 1015  chmod a+x RunLoop.sh
 1016  ./RunLoop.sh
 1018  cd src/ai/abstraction
 1024  code ollama.java    # This is where you can see the prompt. You can modify the prompts here.
 1029  ls -l logs          # The log files are in this subfolder. Check the timestamps. They should be current.
 1030  code Response2025-11-06_09-37-28_LLM_Gemini_One-Shot_RandomBiasedAI_llama3.1:8b.csv     # These are the result files
```
* On the same computer, run the benchmark to get numbers for your own favourite Ollama model
* Try to improve your game AI performance by improving the prompts
 
* to change model name: [ollama.java](https://github.com/drchangliu/MicroRTS/src/ai/abstraction/ollama.java)    in line 121  static String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "llama3.1:8b");
This  "llama3.1:8b" is the modal. If you want to change to a different model, same install the model and paste the model name here. 
* to change the opponent, edit  [config.properties](https://github.com/drchangliu/MicroRTS/resources/config.properties) `AI2=ai.PassiveAI`  AI2 is the other player.
Read the comments; they are self-explanatory.
```
# ai.RandomBiasedAI
# ai.RandomAI
# ai.abstraction.HeavyRush
# ai.abstraction.LightRush
# ai.abstraction.LLM_Gemini
# ai.abstraction.ollama
```

* to change prompt: edit the PROMPT string in the [ollama.java](https://github.com/drchangliu/MicroRTS/src/ai/abstraction/ollama.java) 
* to change map: in line 25 in  [config.properties](https://github.com/drchangliu/MicroRTS/resources/config.properties) " map_location=maps/8x8/basesWorkers8x8.xml "
Now, to change the map, go to the maps folder in the project and paste the proper .xml file.
* to change the number of games available in [Runloop.sh](https://github.com/drchangliu/MicroRTS/RunLoop.sh)
```
TOTAL_RUNS=5                         # << set to 1000 for one thousand runs
RUN_TIME_PER_GAME_SEC="${RUN_TIME_PER_GAME_SEC:-350}"  # << set default seconds per run, it needs to be 500
```
* `TOTAL_RUNS` is how many times you want to run, like 5 times or 10 times.
