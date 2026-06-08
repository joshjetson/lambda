- A user should have a userName.groovy file
  - The userName.groovy file will store user attributes that they create for themself
  - Some user attributes will be private or public depending on whatever the user has set
  - A user might have a public function for example that he stores some trap logic so like
    an easter egg type concept and if another user attempts to execute it the logic may do something like
    steal an item or a bit or a piece of logic.
    - In the groovy file a user will have a name and nickname attribute that they will be able to change and that other users will see.
    - They will also have a tagline attribute. These attributes must all be set to strings and we will use the actual groovy language
    - There will be other attributes as well that a user should be able to set. There will be public and private attributes and properties in this file which is actually a class the 
    lambda user class. 
    As mentioned public attributes will be seen by other players that scan another player. For methods though they would just see a method name not the underlying code that may or may not be nefarious. 


- if a user is being attacked by a frag bot or encounters one in a matrix cooridor other
  users will recieve a notification that tells them a lambda is being attacked and it will list the coordinates
  . The user will then be asked if they would like to fork the repository. If they choose to fork
  they will be taken to the matrix cooridor to join the other player. Doing so will make the frag bot timer
  reset with additional time being added to make it more easy to defeat. The user that defeats the bot will win the items or whatever it drops and not the other player
  . Only users that are further ahead on the same level will get notified with the caveat being for them that
  it would set them back on the map . A user who is in front of a player and being attacked will not trigger a notification for players who are before him in lower levels or in a different map
  


  - A player should have an ls command in where they can see all of their inventory in the form of files
   a user may inspect a file by using vim or cat. Most files will be readonly byt the userName.groovy file for example a user can edit freely and will be used for other matters
   Depending on the lambda avatar the user chooses the user will also be issued a file format which is simply going to be the way their files looks when they use the ls command 


- There is also the idea that there should be in matrix cooridors where more difficult frag bots exist a bare git repo . 
The idea behind a bare git repo is kind of like a save spot. Upon entering a bare repo the user will be able to add or stage files and commit them with a commit message. Then to finalize it a user has to git commit push origin nameOfRepo    the name of the repo will be random funny frag bot names. If a user is defeated they could potentially then when sent back to 0,0 of the current map they could git hard reset which will then take them automatically back to fight the frag bot again with a reset timer. 
They will also regain anything they chose to commit that would have been lost. 
The caveat is that another player could potentially pull the branch and pulling it would set it back to a bare repo
Then if a user upon being sent back to 0,0 from losing if they try to git hard reset it wont work and a message will show up saying repo has been pulled origin is lost



- Puzzle Logic ( )
    - Some Logical Fragments you may not want to trade or sell even if you have doubles.
    some logical fragments will contain either special calculations or will accept special
    parameters or will be a special conditional all related to a specific room which contains 
    a hidden something. The hidden something could potentially be a one of the 4 elemental symbols
    required to defeat the logic daemon
    The hidden something could also potentially be a variable that would need to be passed to a
    previously collected logic fragment that has a function that when called requires the variable
    to be passed to it. The output or return value of the function once called with the
    proper variable or value from the variable will produce the coordinats to where one of the
    hidden elemental symbols resides. Suppose a user is currently at a coordinate which contains a hidden
    elemental symbol. The user will only be able to obtain the elemental symbol if they execute a 
    python or groovy file while also making sure they use the correct command flag along with the correct
    execution command which will be used inside of the python or groovy file as the correct key
    or nonce that will allow the program in the file to produce the elemental symbol effectively decoding
    it first.
    The command flag along with the nonce is what users will be looking for in order to acquire the elemental symbols.
    There will be 4 nonces that need to be acquired by the methods I laid forth. The 4 nonces will have
    some relationship with the type of elemental symbol they unlock. A user will have to be clever 
    in order to know which nonce unlocks which elemental symbol. Even just having the nonce is not good enough.
    A user will have to posess a function which accepts a nonce and ouputs clues for that nonce related to the elemental symbol 
    that they will unlock. 
    For example a nonce related to the elemental symbol of water when passed into the proper function
    might have an output of x1(h)x2(o)
    A user would have to think a little bit about the output
    At that point they would know that the nonce they have will potentially work
    for the elemental symbol water. 
    Even if a user has the proper coordinates to where the elemental symbol is they may not know 
    what the proper command flag is to use or what the proper nonce to use is
    The way that a user finds a nonce would be similar to how they find a command flag for the nonce
    or more appropriately that is paired with the nonce when the python or groovy file is executed.
    It may be the case that a logical fragment has a condition that is something like if (Object == nonce){

        return flag + clue
    }
where clue would be a clue as to what elemental symbol the flag was supposed to be used for.
All of these ideas are half baked and I need you to consider them better but the gist of them are their.
Consider the logic and the puzzle aspect of them . Divise a really good implementation and a way of allowing users to apply these mechanics in the game in a way that will work.
Take from my ideas and make them much better and smarter. 


Currently the issue is the game mechanics are not working entirely correct.
Thee 4 elements are supposed to be hidden.
If an elemental symbol is at a coordinant the user will not and should not know unless:
A. They 

TODO:
The slot machine mini game needs to be fixed
The Logical Fragments need to not be visible in the map
The ability to see Logical Fragments on the map should be a special item
it should only work for adjacent coordinates and only for 10 seconds
Theft needs to be implemented
If you detect a user in an adjacent coordinate or in the same coordinate in 
a similar way to how the repair mini game works you would be able to steal one item
of your choosing even an elemental symbol.
The way it would work is each user is going to have a user file that could potentially be scanned
. Once scanned your user id would become known to the person scanning
Once they grep your user id they would then be able to enter a slot machine mini game where if they 
match all the numbers to your pid they would get to see your inventory and choose one item to steal that you would lose
if you fail the mini game you would lose a random item and it would be given to the attacked
You would have to be quick though because if you wanted another try you can only perform the attack if your on an adjacent coordinate
So while scanning one coordinate a user would come up and showing they were in an adjacent coordinate
lambda detected and their username would show
then you would type scan username
their userfile would cat and you would have to then type grep -o pid username.file
once you get the pid then you would automatically be thrown into a minigame


Game Modes:

- Node Mode
    - Everyman for himself
- Cluster Mode
    - 7 v 7 max
    - Teams of max 7 players per team compete with higher strategy
    - 4 players per coordinates maximum, 2 Players from each team maximum
    - Theft  mechanics works similar to rsync from the onset
      but also with a slot machine mini game mechanism and grep as well
    - The game would end up having a portion of players dedicated to helping the lambda
      not get critical items stolen from their inventory
    - While the other potion of players on a team would be acting as jackals or infiltrators
      of the other team
    - Dont forget about the idea that players could act as decoys
    - There could only be one race per team but two lambda races
    - Special abilities would shine in this game play mode because abilities
      would do things like scanning a player temporarily without detection for 5 secs lets say
      A special ability would also be something that would let a user enter read only mode in where
      they would be able to jump to the next map for 1 minute to gather intelligence in
      read only mode though. Only one race would be able to do this
      There is going to have to be one race that can also gain the ability to control defrag bots
      and place them in strategic places. This would have to be limited though and one they
      should only be able to control one bot at a time with a cool down period. They would be 
      impervious to that frag bot or have immunity from that specific frag bot
    - The only player that will be able to acquire the symbols has to be a pure lambda
      the player could pass the symbols to another player like a football and they
      can hold on the the symbols for a period of time. 
      A pure lambda is also the only one who can defeat a logic daemon
    - Once a logic daemon is defeated the entire team will move through to the next map
    - At the begining of the game once Cluster mode is selected the main lambda user 
      will be able to select how many maps they want to play for the particular game


