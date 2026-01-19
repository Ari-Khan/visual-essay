## Getting Started

Welcome to the VS Code Java world. Here is a guideline to help you get started to write Java code in Visual Studio Code.

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources
- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

> If you want to customize the folder structure, open `.vscode/settings.json` and update the related settings there.

## Dependency Management

The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found [here](https://github.com/microsoft/vscode-java-dependency#manage-dependencies).

## Driving the animation via writing.txt

This app now reads commands (markers) from `src/writing.txt` to control post-typing behavior without hardcoding. Any non-marker lines are treated as the base text and will be typed out first. After typing completes, markers are executed in order.

Supported markers:
- `[DELETE_LAST_SENTENCE]`: Deletes the last sentence from the current text (animated).
- `[PAUSE:N]`: Pauses for `N` frames (~30ms per frame).
- `[INSERT:Your text here]`: Types the provided text and appends it to the current text (animated).
- `[INSERT_LINE:N]`: Types and appends line `N` from `writing.txt` (1-based index).
- `[COMMENT:user:... ]`: Shows a left-side comment bubble with the provided text.
- `[COMMENT:wu:... ]`: Shows a right-side comment bubble with the provided text.

Example `writing.txt`:

```
This is my base essay paragraph that will be typed.
[PAUSE:120]
[COMMENT:user:hi mr. wu, can you look over my thesis?]
[COMMENT:wu:sure, let me take a look]
[DELETE_LAST_SENTENCE]
[PAUSE:120]
[INSERT_LINE:2]
[INSERT:And then I add my original thought back.]
```

Notes:
- Markers are removed from the base text and only affect the animation flow.
- Commands run sequentially after the initial typing finishes. Use `[PAUSE:N]` between markers to control timing.
