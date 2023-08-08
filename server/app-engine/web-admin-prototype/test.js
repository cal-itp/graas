console.log('+ test.js');

function handleDropDownChange(e) {
    console.log('handleDropDownChange()');
    console.log('- e.target: ', e.target);

    const elem = e.target;
    console.log('- elem.value: ', elem.value);
}