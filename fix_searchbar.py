import sys

file_path = 'app/src/main/java/com/kin/easynotes/presentation/screens/home/HomeScreen.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
skip = 0
for i, line in enumerate(lines):
    if skip > 0:
        skip -= 1
        continue

    if 'SearchBar(' in line and 'modifier = Modifier' in lines[i+1]:
        # Found the SearchBar call
        new_lines.append('    SearchBar(\n')
        new_lines.append('        modifier = Modifier\n')
        new_lines.append('            .fillMaxWidth()\n')
        new_lines.append('            .padding(horizontalPadding, 16.dp, horizontalPadding, 18.dp)\n')
        new_lines.append('            .scale(searchBarScale),\n')
        new_lines.append('        inputField = {\n')
        new_lines.append('            SearchBarDefaults.InputField(\n')
        new_lines.append('                query = query,\n')
        new_lines.append('                onQueryChange = onQueryChange,\n')
        new_lines.append('                onSearch = onQueryChange,\n')
        new_lines.append('                expanded = false,\n')
        new_lines.append('                onExpandedChange = {},\n')
        new_lines.append('                placeholder = { Text(stringResource(R.string.search)) },\n')
        new_lines.append('                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },\n')
        new_lines.append('                trailingIcon = {\n')

        # Now we need to find and copy the trailingIcon content
        # It starts around lines[i+8]
        trailing_start = -1
        for j in range(i, i + 20):
            if 'trailingIcon = {' in lines[j]:
                trailing_start = j
                break

        if trailing_start != -1:
            # Found trailingIcon start. Now find the end by matching braces.
            brace_count = 0
            for j in range(trailing_start, len(lines)):
                brace_count += lines[j].count('{')
                brace_count -= lines[j].count('}')
                if 'trailingIcon = {' in lines[j]:
                    # The first line has trailingIcon = {
                    content = lines[j].split('trailingIcon = {')[1]
                    # We don't want to append it yet, we just start the loop
                else:
                    new_lines.append(lines[j])

                if brace_count == 0:
                    # End of trailingIcon block
                    skip_to = j
                    break

            new_lines.append('            )\n')
            new_lines.append('        },\n')
            new_lines.append('        expanded = false,\n')
            new_lines.append('        onExpandedChange = {},\n')

            # Skip until the end of the old SearchBar call
            for j in range(skip_to, len(lines)):
                if 'colors = SearchBarDefaults.colors' in lines[j]:
                    new_lines.append(lines[j])
                    new_lines.append(lines[j+1])
                    skip = j + 1 - i
                    break
        else:
            new_lines.append(line)
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
